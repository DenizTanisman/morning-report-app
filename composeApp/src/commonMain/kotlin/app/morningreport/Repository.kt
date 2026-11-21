package app.morningreport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Everything the app knows, kept in one JSON file. Sections, their sources, the items read from them and the
 * AI settings. Reading feeds happens here; the UI only calls these functions and watches [data].
 */
class Repository(
    private val load: () -> String?,
    private val save: (String) -> Unit,
    /** Replaces the network in the demo recording and in tests. */
    private val fetcher: (Source) -> ParsedFeed? = ::fetchFeed,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val _data = MutableStateFlow(load()?.let { runCatching { json.decodeFromString(AppData.serializer(), it) }.getOrNull() } ?: AppData())
    val data: StateFlow<AppData> = _data
    private val refreshLock = Mutex()

    private fun change(f: (AppData) -> AppData) {
        _data.update(f)
        save(json.encodeToString(AppData.serializer(), _data.value))
    }

    // ---------------------------------------------------------------- sections

    fun addSection(title: String): Section {
        val s = Section(newId(), title.trim())
        change { it.copy(sections = it.sections + s) }
        return s
    }

    fun renameSection(id: String, title: String) = change { d ->
        d.copy(sections = d.sections.map { if (it.id == id) it.copy(title = title.trim()) else it })
    }

    fun deleteSection(id: String) = change { d ->
        val gone = d.sections.firstOrNull { it.id == id }?.sources?.map { it.id }?.toSet() ?: emptySet()
        d.copy(sections = d.sections.filterNot { it.id == id }, items = d.items.filterNot { it.sourceId in gone })
    }

    fun moveSection(id: String, by: Int) = change { d ->
        val i = d.sections.indexOfFirst { it.id == id }
        val j = (i + by).coerceIn(0, d.sections.lastIndex)
        if (i < 0 || i == j) d else d.copy(sections = d.sections.toMutableList().apply { add(j, removeAt(i)) })
    }

    // ---------------------------------------------------------------- sources

    /** Resolves [input] on the IO dispatcher, adds it to the section and reads it once. */
    suspend fun addSource(sectionId: String, kind: SourceKind, input: String, time: String?): Source {
        val resolved = withContext(Dispatchers.IO) { Resolver.resolve(kind, input) }.copy(time = time)
        val dup = _data.value.sections.firstOrNull { it.id == sectionId }?.sources?.any { it.feedUrl == resolved.feedUrl } == true
        if (dup) throw Resolver.Failure("duplicate")
        change { d -> d.copy(sections = d.sections.map { if (it.id == sectionId) it.copy(sources = it.sources + resolved) else it }) }
        refresh(onlyDue = false, only = setOf(resolved.id))
        return resolved
    }

    /** For the demo and tests: adds an already resolved source without touching the network. */
    fun addResolvedSource(sectionId: String, source: Source) = change { d ->
        d.copy(sections = d.sections.map { if (it.id == sectionId) it.copy(sources = it.sources + source) else it })
    }

    fun setSourceTime(sourceId: String, time: String?) = editSource(sourceId) { it.copy(time = time, lastChecked = if (time != null) 0 else it.lastChecked) }

    fun removeSource(sourceId: String) = change { d ->
        d.copy(sections = d.sections.map { s -> s.copy(sources = s.sources.filterNot { it.id == sourceId }) }, items = d.items.filterNot { it.sourceId == sourceId })
    }

    private fun editSource(id: String, f: (Source) -> Source) = change { d ->
        d.copy(sections = d.sections.map { s -> s.copy(sources = s.sources.map { if (it.id == id) f(it) else it }) })
    }

    fun setAi(ai: AiSettings) = change { it.copy(ai = ai) }

    // ---------------------------------------------------------------- reading

    class Result(val newItems: Int, val failed: Int)

    /**
     * Reads sources and merges new items. [onlyDue] = automatic check (respects each source's time);
     * false = the person asked (reads everything, or just [only]). Returns how many items were new.
     */
    suspend fun refresh(onlyDue: Boolean, only: Set<String>? = null): Result = refreshLock.withLock {
        val now = nowMillis()
        val sources = _data.value.sections.flatMap { it.sources }
            .filter { only == null || it.id in only }
            .filter { !onlyDue || Schedule.isDue(it, now) }
        if (sources.isEmpty()) return Result(0, 0)

        val gate = Semaphore(6)
        val results = withContext(Dispatchers.IO) {
            coroutineScope {
                sources.map { s -> async { gate.withPermit { s to runCatching { fetcher(s) } } } }.awaitAll()
            }
        }

        val known = _data.value.items.associateBy { it.id }
        val fresh = mutableListOf<Item>()
        val checked = mutableMapOf<String, String?>()
        for ((source, r) in results) {
            val feed = r.getOrNull()
            if (feed == null) { checked[source.id] = r.exceptionOrNull()?.message ?: "Could not read the feed."; continue }
            checked[source.id] = null
            feed.entries.take(PER_SOURCE).forEach { e ->
                val id = source.id + ":" + e.id
                if (id !in known && fresh.none { it.id == id }) {
                    val (title, summary) = tidy(source, e.title, e.summary)
                    fresh += Item(id, source.id, title, e.link, e.published.takeIf { it > 0 } ?: now, summary.take(400), e.thumbnail, now)
                }
            }
        }

        // Optional AI pass, only over the new items.
        var scored = fresh.toList()
        val ai = _data.value.ai
        var aiError: String? = ai.lastError
        if (ai.enabled && fresh.isNotEmpty()) {
            val names = sources.associate { it.id to it.name }
            aiError = try {
                val scores = withContext(Dispatchers.IO) { AiFilter.score(ai, fresh.map { it to (names[it.sourceId] ?: "") }) }
                scored = fresh.map { it.copy(score = scores[it.id]) }
                null
            } catch (e: Exception) {
                e.message ?: e::class.simpleName
            }
        }

        change { d ->
            val alive = d.sections.flatMap { it.sources }.map { it.id }.toSet()
            val merged = (d.items + scored).filter { it.sourceId in alive }
                .filter { now - maxOf(it.published, 0) < KEEP_MS || it.fetchedAt > now - 60_000 }
                .sortedByDescending { it.published }
                .groupBy { it.sourceId }.values.flatMap { it.take(KEEP_PER_SOURCE) }
                .sortedByDescending { it.published }
            d.copy(
                items = merged,
                sections = d.sections.map { s ->
                    s.copy(sources = s.sources.map { src ->
                        if (src.id in checked) src.copy(lastChecked = now, lastError = checked[src.id]) else src
                    })
                },
                ai = d.ai.copy(lastError = if (ai.enabled) aiError else null),
            )
        }
        Result(fresh.size, checked.count { it.value != null })
    }

    /** Scores the visible items again, e.g. after the interests were edited. */
    suspend fun rescoreAll(): String? = refreshLock.withLock {
        val d = _data.value
        if (!d.ai.enabled) return null
        val names = d.sections.flatMap { it.sources }.associate { it.id to it.name }
        return try {
            val scores = withContext(Dispatchers.IO) { AiFilter.score(d.ai, d.items.take(120).map { it to (names[it.sourceId] ?: "") }) }
            change { x -> x.copy(items = x.items.map { i -> scores[i.id]?.let { i.copy(score = it) } ?: i }, ai = x.ai.copy(lastError = null)) }
            null
        } catch (e: Exception) {
            val msg = e.message ?: "AI error"
            change { x -> x.copy(ai = x.ai.copy(lastError = msg)) }
            msg
        }
    }

    companion object {
        const val PER_SOURCE = 15
        const val KEEP_PER_SOURCE = 30
        const val KEEP_MS = 7L * 24 * 3600 * 1000

        /** Google News titles end in " - Publisher" and its summary only repeats the title; most feeds need nothing. */
        fun tidy(source: Source, title: String, summary: String): Pair<String, String> {
            var t = title
            if (source.feedUrl.contains("news.google.com/")) t = t.substringBeforeLast(" - ").ifBlank { t }
            val sum = summary.takeUnless { it.isBlank() || it.startsWith(t) || it.startsWith(title) || t.startsWith(it) } ?: ""
            return t to sum
        }

        fun fetchFeed(source: Source): ParsedFeed? {
            val (url, body) = Http.get(source.feedUrl, "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
            return Xml.parseFeed(body, url) ?: throw IllegalStateException("Not a feed any more.")
        }
    }
}
