package app.morningreport

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

/** Plain HTTP access shared by Android and desktop (both JVM, see jvmShared). */
expect object Http {
    /** GET [url]; returns the final URL after redirects and the body. Throws on network errors and non-2xx. */
    fun get(url: String, accept: String = "*/*"): Pair<String, String>
    fun getBytes(url: String): ByteArray
    fun postJson(url: String, body: String, bearer: String?): String
}

/** RSS 2.0 / Atom parsing with the platform XML parser. */
expect object Xml {
    fun parseFeed(xml: String, base: String): ParsedFeed?
}

class ParsedFeed(val title: String, val link: String?, val entries: List<ParsedEntry>)

class ParsedEntry(
    val id: String,
    val title: String,
    val link: String,
    val published: Long,
    val summary: String,
    val thumbnail: String?,
)

expect fun decodeImage(bytes: ByteArray): ImageBitmap?

expect fun nowMillis(): Long

/** Hour and minute of [millis] in the device's time zone, and the start of that day. */
expect fun localTime(millis: Long): Triple<Int, Int, Long>

expect fun systemLanguage(): String

/** "Sat, Nov 21" / "21 Kas Cmt": the day shown in the top bar. */
expect fun dayLabel(millis: Long, tr: Boolean): String

/** Whether videos play inside the app (Android) or open in the browser / YouTube (desktop: no embedded browser). */
expect val inAppVideo: Boolean

/** The YouTube player for [videoId]; [onEnded] when the video finishes. Only used where [inAppVideo]. */
@Composable
expect fun YouTubeView(videoId: String, modifier: Modifier, onEnded: () -> Unit)
