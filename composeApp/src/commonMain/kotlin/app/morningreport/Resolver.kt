package app.morningreport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Turns whatever the person typed into a readable source.
 *
 * YouTube: "@handle", a channel/handle/video URL, or a "UC…" channel id → the channel's public RSS feed.
 * News: a feed URL, a site URL or a bare domain → the site's RSS/Atom feed, found from the page's
 * `<link rel="alternate">` or, failing that, from the usual feed paths.
 */
object Resolver {
    class Failure(message: String) : Exception(message)

    private val CHANNEL_ID = Regex("^UC[A-Za-z0-9_-]{22}$")

    suspend fun resolve(kind: SourceKind, input: String): Source = when (kind) {
        SourceKind.YOUTUBE -> youtube(input.trim())
        SourceKind.NEWS -> news(input.trim())
    }

    // ---------------------------------------------------------------- YouTube

    fun youtubePageUrl(input: String): String {
        val s = input.trim()
        return when {
            s.isEmpty() -> throw Failure("empty")
            CHANNEL_ID.matches(s) -> "https://www.youtube.com/channel/$s"
            s.startsWith("@") -> "https://www.youtube.com/" + s
            s.startsWith("http://") || s.startsWith("https://") -> s
            s.contains("youtube.com") || s.contains("youtu.be") -> "https://$s"
            // A bare word is treated as a handle, which is what people usually copy.
            Regex("^[A-Za-z0-9._-]{3,}$").matches(s) -> "https://www.youtube.com/@$s"
            else -> throw Failure("not_youtube")
        }
    }

    private fun youtube(input: String): Source {
        val pageUrl = youtubePageUrl(input)
        val host = hostOf(pageUrl)
        if (!(host.endsWith("youtube.com") || host == "youtu.be")) throw Failure("not_youtube")
        // A channel id in the address is the answer already; the page would also list other channels.
        val given = Regex("/channel/(UC[A-Za-z0-9_-]{22})").find(pageUrl)?.groupValues?.get(1)
        val html = if (given != null) "" else try { Http.get(pageUrl, "text/html").second } catch (e: Exception) {
            // YouTube answers 404 for a handle that does not exist: that is a wrong name, not a network problem.
            throw Failure(if (e.message?.startsWith("HTTP 404") == true) "channel_not_found" else "youtube_unreachable")
        }
        val id = given ?: channelIdFromPage(html, isVideo = Regex("/(watch|shorts/|live/)|youtu\\.be/").containsMatchIn(pageUrl))
            ?: throw Failure("channel_not_found")
        val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$id"
        // The feed's title is the channel's name; for a video URL the page title would be the video's.
        val feedTitle = runCatching { Xml.parseFeed(Http.get(feedUrl, "application/atom+xml").second, feedUrl)?.title }.getOrNull()
        val name = feedTitle?.takeIf { it.isNotBlank() }
            ?: Regex("<meta property=\"og:title\" content=\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.let(::unescape)
            ?: id
        return Source(newId(), SourceKind.YOUTUBE, name, feedUrl, "https://www.youtube.com/channel/$id")
    }

    /**
     * The page names many channels (related, featured, the header of a sister channel…). Only these places name
     * the page's own channel: the canonical link and the header metadata on a channel page, the video's owner on
     * a video page.
     */
    fun channelIdFromPage(html: String, isVideo: Boolean): String? {
        val id = "(UC[A-Za-z0-9_-]{22})"
        val channelPage = listOf(
            Regex("<link rel=\"canonical\" href=\"https://www\\.youtube\\.com/channel/$id\""),
            Regex("<meta itemprop=\"identifier\" content=\"$id\""),
            Regex("\"channelMetadataRenderer\":\\{[^}]*?\"externalId\":\"$id\""),
            Regex("\"externalId\":\"$id\""),
        )
        val videoPage = listOf(
            Regex("<meta itemprop=\"channelId\" content=\"$id\""),
            Regex("\"videoDetails\":\\{[^}]*?\"channelId\":\"$id\""),
            Regex("\"videoOwnerRenderer\".{0,2000}?\"browseId\":\"$id\""),
        )
        for (r in if (isVideo) videoPage + channelPage else channelPage + videoPage) r.find(html)?.let { return it.groupValues[1] }
        return null
    }

    // ---------------------------------------------------------------- News

    private suspend fun news(input: String): Source {
        if (input.isEmpty()) throw Failure("empty")
        val start = if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
        if (!hostOf(start).contains('.')) throw Failure("not_a_site")
        val (finalUrl, body) = try { Http.get(start, "application/rss+xml, application/atom+xml, text/html;q=0.9, */*;q=0.8", timeoutMs = 12_000) }
            catch (e: Exception) { return googleNews(start, null) ?: throw Failure("site_unreachable") }

        // 1. It already is a feed.
        Xml.parseFeed(body, finalUrl)?.let { feed ->
            val site = feed.link ?: origin(finalUrl)
            return Source(newId(), SourceKind.NEWS, feed.title.ifBlank { hostOf(site) }, finalUrl, site)
        }

        // 2. The page announces its feed.
        val candidates = mutableListOf<String>()
        Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(body).forEach { m ->
            val tag = m.value
            val type = attr(tag, "type")?.lowercase() ?: return@forEach
            val rel = attr(tag, "rel")?.lowercase() ?: ""
            if ("alternate" in rel && (type.contains("rss") || type.contains("atom"))) {
                attr(tag, "href")?.let { candidates += absolute(finalUrl, unescape(it)) }
            }
        }
        // 3. The usual places, next to the page first (bbc.com/sport → bbc.com/sport/rss.xml), then at the root.
        val o = origin(finalUrl)
        val path = finalUrl.substringAfter("://").substringAfter('/', "").substringBefore('?').trimEnd('/')
        if (path.isNotEmpty()) candidates += listOf("/rss.xml", "/feed", "/rss", "/index.xml").map { "$o/$path$it" }
        candidates += listOf("/feed", "/rss", "/rss.xml", "/feed.xml", "/atom.xml", "/index.xml", "/feeds/posts/default").map { o + it }

        // All at once with a short timeout: some sites answer slowly or not at all on paths they don't have.
        val probes = candidates.distinct().take(14)
        val results = coroutineScope {
            probes.map { c ->
                async(Dispatchers.IO) {
                    runCatching { Http.get(c, "application/rss+xml, application/atom+xml, */*", timeoutMs = 8_000) }
                        .mapCatching { (u, b) -> u to Xml.parseFeed(b, u) }.getOrNull()
                }
            }.awaitAll()
        }
        for (feed in results) {
            if (feed == null) continue
            val parsed = feed.second ?: continue
            if (parsed.entries.isEmpty() && parsed.title.isBlank()) continue
            val pageTitle = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.let(::unescape)?.trim()
            val name = parsed.title.ifBlank { pageTitle ?: hostOf(o) }
            return Source(newId(), SourceKind.NEWS, name, feed.first, parsed.link ?: o)
        }
        return googleNews(finalUrl, body) ?: throw Failure("feed_not_found")
    }

    /**
     * Last resort: Google News' public search feed restricted to the site. Works for almost any news site,
     * links go through Google News. The name says so, so the person knows where the list comes from.
     */
    private fun googleNews(url: String, html: String?): Source? {
        val host = hostOf(url).removePrefix("www.")
        val path = url.substringAfter("://").substringAfter('/', "").substringBefore('?').trim('/')
        // Google News only lists a site well in the site's own language and region: aa.com.tr/tr/ekonomi gives one
        // result in the US edition and a hundred in the Turkish one. The page says its language; else the domain does.
        val lang = html?.let { Regex("<html[^>]*\\slang=[\"']?([A-Za-z]{2})", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.lowercase() }
            ?: TLD_LANG[host.substringAfterLast('.')] ?: "en"
        val edition = EDITIONS[lang] ?: EDITIONS.getValue("en")
        val queries = buildList {
            if (path.isNotEmpty() && !path.contains('.')) add("site:$host/$path")
            path.split('/').lastOrNull { it.length > 2 && !it.contains('.') && it != lang }?.let { add("site:$host $it") }
            add("site:$host")
        }
        var best: Pair<String, ParsedFeed>? = null
        for (q in queries) {
            val feedUrl = "https://news.google.com/rss/search?q=" + q.replace(":", "%3A").replace("/", "%2F").replace(" ", "+") + "&" + edition
            val feed = runCatching { Xml.parseFeed(Http.get(feedUrl, "application/rss+xml", timeoutMs = 10_000).second, feedUrl) }.getOrNull() ?: continue
            if (best == null || feed.entries.size > best.second.entries.size) best = feedUrl to feed
            if (feed.entries.size >= 10) break // the narrowest query that still has enough news
        }
        val (feedUrl, feed) = best ?: return null
        if (feed.entries.isEmpty()) return null
        return Source(newId(), SourceKind.NEWS, "$host · Google News", feedUrl, "https://$host")
    }

    private val TLD_LANG = mapOf("tr" to "tr", "de" to "de", "at" to "de", "fr" to "fr", "es" to "es", "mx" to "es", "it" to "it",
        "pt" to "pt", "br" to "pt", "nl" to "nl", "jp" to "ja", "ru" to "ru", "pl" to "pl")
    private val EDITIONS = mapOf(
        "en" to "hl=en-US&gl=US&ceid=US:en", "tr" to "hl=tr&gl=TR&ceid=TR:tr", "de" to "hl=de&gl=DE&ceid=DE:de",
        "fr" to "hl=fr&gl=FR&ceid=FR:fr", "es" to "hl=es-419&gl=MX&ceid=MX:es-419", "it" to "hl=it&gl=IT&ceid=IT:it",
        "pt" to "hl=pt-BR&gl=BR&ceid=BR:pt-419", "nl" to "hl=nl&gl=NL&ceid=NL:nl", "ja" to "hl=ja&gl=JP&ceid=JP:ja",
        "ru" to "hl=ru&gl=RU&ceid=RU:ru", "pl" to "hl=pl&gl=PL&ceid=PL:pl",
    )

    // ---------------------------------------------------------------- helpers

    private fun attr(tag: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE).find(tag)?.let { m ->
            m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
        }

    fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':').lowercase()

    fun origin(url: String): String = url.substringBefore("://") + "://" + url.substringAfter("://").substringBefore('/').substringBefore('?')

    // Schemes are case-insensitive: some sites redirect to "HTTPS://…".
    fun absolute(base: String, href: String): String = when {
        href.startsWith("http://", ignoreCase = true) || href.startsWith("https://", ignoreCase = true) ->
            href.substringBefore("://").lowercase() + "://" + href.substringAfter("://")
        href.startsWith("//") -> base.substringBefore("://") + ":" + href
        href.startsWith("/") -> origin(base) + href
        else -> base.substringBeforeLast('/') + "/" + href
    }

    fun unescape(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&#039;", "'").replace("&apos;", "'")
}

private var idCounter = 0
fun newId(): String = (nowMillis().toString(36) + (idCounter++).toString(36) + (0..1295).random().toString(36))
