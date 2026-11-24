package app.morningreport

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Talks to real sites. Skipped unless LIVE=1, so CI and offline builds stay green:
 *   LIVE=1 ./gradlew :composeApp:desktopTest --tests '*LiveTest*'
 */
class LiveTest {
    private val live = System.getenv("LIVE") == "1"

    private fun check(kind: SourceKind, input: String) {
        val s = kotlinx.coroutines.runBlocking { Resolver.resolve(kind, input) }
        val feed = Repository.fetchFeed(s)!!
        println("OK  ${kind.name.padEnd(7)} ${input.padEnd(22)} → ${s.name} | ${s.feedUrl} | ${feed.entries.size} items | ${feed.entries.firstOrNull()?.title}")
        assertTrue(feed.entries.isNotEmpty(), input)
    }

    @Test fun demoAndCommonSources() {
        if (!live) return
        val failures = mutableListOf<String>()
        Demo.plans.flatMap { it.sources }.map { it.kind to it.input }.plus(listOf(
            SourceKind.NEWS to "bbc.com/turkce",
            SourceKind.NEWS to "https://feeds.bbci.co.uk/news/business/rss.xml",
            SourceKind.NEWS to "aa.com.tr",
            SourceKind.NEWS to "bloomberght.com",
            SourceKind.NEWS to "aa.com.tr/tr/ekonomi",
            SourceKind.NEWS to "arstechnica.com",
            SourceKind.NEWS to "espn.com",
            SourceKind.YOUTUBE to "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            SourceKind.YOUTUBE to "https://www.youtube.com/channel/UCHnyfMqiRRG1u-2MsSQLbXA",
        )).forEach { (k, i) -> runCatching { check(k, i) }.onFailure { failures += "$i: ${it.message}"; println("ERR ${k.name} $i: ${it.message}") } }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}

/** Needs a local Ollama with gemma3:4b: LIVE_AI=1 ./gradlew :composeApp:desktopTest --tests '*LiveAiTest*' */
class LiveAiTest {
    @Test fun scoresAgainstInterests() {
        if (System.getenv("LIVE_AI") != "1") return
        val titles = listOf(
            "Lakers beat Warriors in overtime thriller",
            "NASA's Voyager 1 is now a light-day away",
            "New smartphone case colors announced",
            "Celebrity couple spotted at a restaurant",
            "James Webb telescope finds water on distant planet",
            "Rockets vs. Nuggets final score",
        )
        val items = titles.mapIndexed { i, t -> Item("$i", "s", t, "https://x/$i", 0) to "Demo" }
        val ai = AiSettings(enabled = true, interests = "NBA basketball and space science. Not gadgets, not celebrity gossip.")
        val t0 = System.currentTimeMillis()
        val scores = AiFilter.score(ai, items)
        titles.forEachIndexed { i, t -> println("AI ${scores["$i"]}  $t") }
        println("AI took ${System.currentTimeMillis() - t0} ms")
        assertTrue(scores.size == titles.size)
        assertTrue(scores["0"]!! > scores["3"]!! && scores["1"]!! > scores["2"]!!)
    }
}
