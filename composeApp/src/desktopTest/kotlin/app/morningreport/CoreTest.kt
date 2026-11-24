package app.morningreport

import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    private val rss = """<?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/" xmlns:content="http://purl.org/rss/1.0/modules/content/">
        <channel><title>Example News</title><link>https://example.com/</link>
        <item><title>First &amp; best</title><link>https://example.com/a</link><guid>a</guid>
          <pubDate>Fri, 20 Nov 2026 07:30:00 +0300</pubDate><description><![CDATA[<p>Hello <b>world</b></p><img src="/i.jpg">]]></description></item>
        <item><title>Second</title><link>https://example.com/b</link><pubDate>Thu, 19 Nov 2026 10:00:00 GMT</pubDate>
          <media:thumbnail url="https://example.com/t.jpg"/></item>
        </channel></rss>"""

    private val atom = """<?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/" xmlns:yt="http://www.youtube.com/xml/schemas/2015">
        <title>Some Channel</title><link rel="alternate" href="https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv"/>
        <entry><id>yt:video:abc</id><title>A video</title><link rel="alternate" href="https://www.youtube.com/watch?v=abc"/>
          <published>2026-11-20T06:00:00+00:00</published>
          <media:group><media:thumbnail url="https://i1.ytimg.com/vi/abc/hqdefault.jpg" width="480" height="360"/><media:description>Desc</media:description></media:group>
        </entry></feed>"""

    @Test fun parsesRss() {
        val f = assertNotNull(Xml.parseFeed(rss, "https://example.com/feed"))
        assertEquals("Example News", f.title)
        assertEquals(2, f.entries.size)
        assertEquals("First & best", f.entries[0].title)
        assertEquals("Hello world", f.entries[0].summary)
        assertEquals("https://example.com/i.jpg", f.entries[0].thumbnail)
        assertEquals("https://example.com/t.jpg", f.entries[1].thumbnail)
        assertEquals(java.time.OffsetDateTime.parse("2026-11-20T07:30:00+03:00").toInstant().toEpochMilli(), f.entries[0].published)
    }

    @Test fun parsesAtomYoutube() {
        val f = assertNotNull(Xml.parseFeed(atom, "https://www.youtube.com/feeds/videos.xml?channel_id=x"))
        assertEquals("Some Channel", f.title)
        val e = f.entries.single()
        assertEquals("https://www.youtube.com/watch?v=abc", e.link)
        assertEquals("https://i1.ytimg.com/vi/abc/hqdefault.jpg", e.thumbnail)
        assertEquals("Desc", e.summary)
        assertEquals("https://i1.ytimg.com/vi/abc/mqdefault.jpg", betterThumb(e.thumbnail!!))
    }

    @Test fun htmlIsNotAFeed() {
        assertNull(Xml.parseFeed("<!doctype html><html><head><title>x</title></head></html>", "https://x.com"))
        assertNull(Xml.parseFeed("<?xml version=\"1.0\"?><html><body/></html>", "https://x.com"))
    }

    @Test fun times() {
        assertEquals("07:05", Schedule.normalize("7:05"))
        assertEquals("07:30", Schedule.normalize("0730"))
        assertEquals("23:59", Schedule.normalize(" 23:59 "))
        assertNull(Schedule.normalize("24:00"))
        assertNull(Schedule.normalize("7.30"))
    }

    @Test fun dueOnceADayAfterItsTime() {
        val day = LocalDate.of(2026, 11, 20).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val h = 3600_000L
        val src = Source("s", SourceKind.NEWS, "n", "f", "x", time = "08:00")
        assertTrue(!Schedule.isDue(src, day + 7 * h))                              // before its time
        assertTrue(Schedule.isDue(src, day + 9 * h))                               // after, never read today
        assertTrue(!Schedule.isDue(src.copy(lastChecked = day + 8 * h + 60_000), day + 12 * h)) // already read today
        assertTrue(Schedule.isDue(src.copy(lastChecked = day - 2 * h), day + 8 * h)) // read yesterday
        val free = src.copy(time = null, lastChecked = day)
        assertTrue(!Schedule.isDue(free, day + 10 * 60_000))
        assertTrue(Schedule.isDue(free, day + 30 * 60_000))
    }

    @Test fun aiAnswers() {
        assertEquals(listOf(7, 2, 9), AiFilter.parseScores("""{"scores":[7,2,9]}"""))
        assertEquals(listOf(3, 8), AiFilter.parseScores("Sure!\n```json\n{\"scores\": [3, 8]}\n```"))
        assertEquals(listOf(5, 6), AiFilter.parseScores("[5, 6.0]"))
    }

    @Test fun youtubeInputs() {
        assertEquals("https://www.youtube.com/@veritasium", Resolver.youtubePageUrl("@veritasium"))
        assertEquals("https://www.youtube.com/@veritasium", Resolver.youtubePageUrl("veritasium"))
        assertEquals("https://www.youtube.com/channel/UCHnyfMqiRRG1u-2MsSQLbXA", Resolver.youtubePageUrl("UCHnyfMqiRRG1u-2MsSQLbXA"))
        assertEquals("https://youtube.com/watch?v=x", Resolver.youtubePageUrl("youtube.com/watch?v=x"))
    }

    @Test fun repositoryMergesAndKeepsOnDisk() = runBlocking {
        var saved: String? = null
        val feed = ParsedFeed("Example", null, listOf(ParsedEntry("1", "One", "https://e/1", nowMillis() - 1000, "", null)))
        val repo = Repository({ saved }, { saved = it }, fetcher = { feed })
        val sec = repo.addSection("Economy")
        repo.addResolvedSource(sec.id, Source("src", SourceKind.NEWS, "Example", "https://e/feed", "https://e"))
        assertEquals(1, repo.refresh(onlyDue = false).newItems)
        assertEquals(0, repo.refresh(onlyDue = false).newItems) // same entry again
        val again = Repository({ saved }, { saved = it }, fetcher = { feed })
        assertEquals("Economy", again.data.value.sections.single().title)
        assertEquals(1, again.data.value.items.size)
        again.removeSource("src")
        assertTrue(again.data.value.items.isEmpty())
    }
}

class ResolverHelpersTest {
    @Test fun upperCaseSchemeRedirect() {
        kotlin.test.assertEquals("https://www.bloomberght.com/", Resolver.absolute("https://bloomberght.com/", "HTTPS://www.bloomberght.com/"))
        kotlin.test.assertEquals("https://a.com/x", Resolver.absolute("https://a.com/y/z", "/x"))
    }

    @Test fun googleNewsHeadlessItemsAreDropped() {
        val src = Source("g", SourceKind.NEWS, "x", "https://news.google.com/rss/search?q=site:x", "https://x")
        kotlin.test.assertEquals("", Repository.tidy(src, " - Bloomberght", "").first)
        kotlin.test.assertEquals("Dolar rekor kırdı", Repository.tidy(src, "Dolar rekor kırdı - Bloomberght", "Dolar rekor kırdı Bloomberght").first)
    }
}
