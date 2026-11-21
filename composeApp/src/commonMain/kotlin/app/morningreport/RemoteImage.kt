package app.morningreport

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** A small in-memory image cache; thumbnails are re-fetched after the app restarts. */
private object ImageCache {
    private val map = LinkedHashMap<String, ImageBitmap>(64, 0.75f, true)
    private const val MAX = 120
    fun get(url: String) = map[url]
    fun put(url: String, img: ImageBitmap) {
        map[url] = img
        if (map.size > MAX) map.remove(map.keys.first())
    }
}

/** YouTube's 16:9 thumbnail instead of the letterboxed 4:3 one the feed lists. */
fun betterThumb(url: String): String = url.replace("/hqdefault.jpg", "/mqdefault.jpg")

@Composable
fun RemoteImage(url: String?, placeholder: Color, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val img by produceState(url?.let { ImageCache.get(it) }, url) {
        if (url == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { decodeImage(Http.getBytes(url)) }.getOrNull()
        }?.also { ImageCache.put(url, it) }
    }
    val bitmap = img
    if (bitmap != null) Image(bitmap, null, modifier, contentScale = scale)
    else Box(modifier.background(placeholder))
}
