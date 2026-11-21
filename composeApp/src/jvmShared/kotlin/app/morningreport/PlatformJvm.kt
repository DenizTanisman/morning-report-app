package app.morningreport

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

actual object Http {
    // A desktop browser string: YouTube serves a different page to anything that looks like Android.
    private const val UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36 MorningReport/1.0"
    private const val MAX_BYTES = 8 * 1024 * 1024

    private fun open(url: String, accept: String): Pair<String, HttpURLConnection> {
        var current = url
        repeat(7) {
            val c = URI(current).toURL().openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", accept)
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9,tr;q=0.8")
            // Skips YouTube's cookie consent page in the EU.
            if (Resolver.hostOf(current).endsWith("youtube.com")) c.setRequestProperty("Cookie", "SOCS=CAI; CONSENT=YES+1")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IllegalStateException("HTTP $code")
                c.disconnect()
                current = Resolver.absolute(current, loc)
                return@repeat
            }
            if (code !in 200..299) { c.disconnect(); throw IllegalStateException("HTTP $code") }
            return current to c
        }
        throw IllegalStateException("Too many redirects.")
    }

    private fun readAll(c: HttpURLConnection): ByteArray = c.inputStream.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BYTES) throw IllegalStateException("Response too large.")
        }
        out.toByteArray()
    }

    actual fun get(url: String, accept: String): Pair<String, String> {
        val (finalUrl, c) = open(url, accept)
        try {
            val bytes = readAll(c)
            return finalUrl to decode(bytes, c.contentType)
        } finally { c.disconnect() }
    }

    actual fun getBytes(url: String): ByteArray {
        val (_, c) = open(url, "image/*")
        try { return readAll(c) } finally { c.disconnect() }
    }

    actual fun postJson(url: String, body: String, bearer: String?): String {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15_000
            c.readTimeout = 180_000 // local models can be slow on the first call while they load
            c.setRequestProperty("Content-Type", "application/json")
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer $bearer")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            if (code !in 200..299) throw IllegalStateException("HTTP $code ${text.take(200)}")
            return text
        } catch (e: java.net.ConnectException) {
            throw IllegalStateException("Cannot reach $url. Is the model server running?")
        } finally { c.disconnect() }
    }

    /** Header charset, then the XML/HTML declaration, then UTF-8. Many Turkish feeds still use ISO-8859-9. */
    private fun decode(bytes: ByteArray, contentType: String?): String {
        val head = String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1)
        val name = contentType?.let { Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
            ?: Regex("<\\?xml[^>]*encoding=[\"']([\\w-]+)[\"']", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
            ?: Regex("<meta[^>]*charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
        val cs = name?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(bytes, cs)
    }
}

actual object Xml {
    private val RFC822 = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm Z", "dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss",
    )

    actual fun parseFeed(xml: String, base: String): ParsedFeed? {
        val t = xml.trimStart('﻿', ' ', '\n', '\r', '\t')
        if (!(t.startsWith("<?xml") || t.startsWith("<rss") || t.startsWith("<feed") || t.startsWith("<rdf"))) return null
        // An XML prolog alone is not enough: HTML pages sometimes have one.
        if (!Regex("<(rss|feed|rdf:RDF)[\\s>]").containsMatchIn(t.take(4000))) return null
        val doc = try {
            val f = DocumentBuilderFactory.newInstance()
            f.isNamespaceAware = true
            runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { f.isExpandEntityReferences = false }
            // Re-encode as UTF-8 and drop the declared encoding: the text is already decoded.
            val clean = t.replaceFirst(Regex("^<\\?xml[^>]*\\?>"), "")
            f.newDocumentBuilder().parse(ByteArrayInputStream(clean.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) { return null }
        val root = doc.documentElement ?: return null
        return when (root.localName ?: root.nodeName) {
            "feed" -> atom(root, base)
            "rss", "RDF" -> rss(root, base)
            else -> null
        }
    }

    private fun atom(root: Element, base: String): ParsedFeed {
        val entries = children(root, "entry").map { e ->
            val link = children(e, "link").firstOrNull { (it.getAttribute("rel").ifEmpty { "alternate" }) == "alternate" }?.getAttribute("href") ?: ""
            val media = descendants(e, "thumbnail").firstOrNull()?.getAttribute("url")
            val summary = text(e, "summary") ?: text(e, "content") ?: descendants(e, "description").firstOrNull()?.textContent ?: ""
            val id = text(e, "id") ?: link
            ParsedEntry(
                id = id.ifEmpty { text(e, "title") ?: "" },
                title = clean(text(e, "title") ?: ""),
                link = Resolver.absolute(base, link.ifEmpty { base }),
                published = date(text(e, "published") ?: text(e, "updated")),
                summary = clean(summary),
                thumbnail = media ?: firstImage(summary, base),
            )
        }
        val link = children(root, "link").firstOrNull { (it.getAttribute("rel").ifEmpty { "alternate" }) == "alternate" }?.getAttribute("href")
        return ParsedFeed(clean(text(root, "title") ?: ""), link?.takeIf { it.startsWith("http") }, entries.filter { it.title.isNotBlank() })
    }

    private fun rss(root: Element, base: String): ParsedFeed {
        val channel = children(root, "channel").firstOrNull() ?: root
        val items = descendants(root, "item")
        val entries = items.map { e ->
            val link = text(e, "link")?.trim().orEmpty().ifEmpty { text(e, "guid")?.trim().orEmpty() }
            val raw = children(e, "encoded").firstOrNull()?.textContent ?: text(e, "description") ?: ""
            val thumb = descendants(e, "thumbnail").firstOrNull()?.getAttribute("url")?.takeIf { it.isNotBlank() }
                ?: descendants(e, "content").firstOrNull { it.getAttribute("url").isNotBlank() && it.getAttribute("medium").let { m -> m.isEmpty() || m == "image" } }?.getAttribute("url")
                ?: children(e, "enclosure").firstOrNull { it.getAttribute("type").startsWith("image") }?.getAttribute("url")
                ?: firstImage(raw, base)
            ParsedEntry(
                id = (text(e, "guid") ?: link).trim().ifEmpty { text(e, "title") ?: "" },
                title = clean(text(e, "title") ?: ""),
                link = if (link.startsWith("http")) link else Resolver.absolute(base, link.ifEmpty { base }),
                published = date(text(e, "pubDate") ?: text(e, "date") ?: text(e, "published")),
                summary = clean(raw),
                thumbnail = thumb,
            )
        }
        val link = text(channel, "link")?.trim()?.takeIf { it.startsWith("http") }
        return ParsedFeed(clean(text(channel, "title") ?: ""), link, entries.filter { it.title.isNotBlank() })
    }

    private fun children(e: Element, name: String): List<Element> {
        val out = mutableListOf<Element>()
        var n: Node? = e.firstChild
        while (n != null) {
            if (n is Element && (n.localName ?: n.nodeName.substringAfter(':')) == name) out += n
            n = n.nextSibling
        }
        return out
    }

    private fun descendants(e: Element, name: String): List<Element> {
        val list = e.getElementsByTagNameNS("*", name)
        return (0 until list.length).map { list.item(it) as Element }
    }

    private fun text(e: Element, name: String): String? = children(e, name).firstOrNull()?.textContent

    private fun firstImage(html: String, base: String): String? =
        Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?.let { Resolver.absolute(base, Resolver.unescape(it)) }?.takeIf { it.startsWith("http") }

    fun clean(s: String): String = Resolver.unescape(
        s.replace(Regex("<(script|style)[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<[^>]+>"), " ")
    ).replace("&nbsp;", " ").replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
        .replace(Regex("\\s+"), " ").trim()

    fun date(s: String?): Long {
        val v = s?.trim().orEmpty()
        if (v.isEmpty()) return 0
        runCatching { return OffsetDateTime.parse(v).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(v).toEpochMilli() }
        runCatching { return ZonedDateTime.parse(v).toInstant().toEpochMilli() }
        for (p in RFC822) runCatching { return SimpleDateFormat(p, Locale.US).parse(v)!!.time }
        runCatching { return LocalDate.parse(v.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        return 0
    }
}

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun localTime(millis: Long): Triple<Int, Int, Long> {
    val z = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return Triple(z.hour, z.minute, z.toLocalDate().atStartOfDay(z.zone).toInstant().toEpochMilli())
}

actual fun systemLanguage(): String = Locale.getDefault().language
