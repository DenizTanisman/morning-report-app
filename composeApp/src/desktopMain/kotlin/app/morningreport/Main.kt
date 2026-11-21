package app.morningreport

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import org.jetbrains.skia.Image
import java.io.File

actual fun decodeImage(bytes: ByteArray): ImageBitmap? = runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

/** macOS: ~/Library/Application Support, Windows: %APPDATA%, Linux: ~/.local/share. MORNING_REPORT_DATA overrides. */
fun dataDir(): File {
    System.getenv("MORNING_REPORT_DATA")?.let { return File(it).apply { mkdirs() } }
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    val dir = when {
        os.contains("mac") -> File(home, "Library/Application Support/Morning Report")
        os.contains("win") -> File(System.getenv("APPDATA") ?: home, "Morning Report")
        else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "morning-report")
    }
    return dir.apply { mkdirs() }
}

fun main(args: Array<String>) {
    val file = File(dataDir(), "data.json")
    val repo = Repository(
        load = { file.takeIf { it.exists() }?.readText() },
        save = { text -> File(file.parentFile, "data.json.tmp").apply { writeText(text) }.renameTo(file) || run { file.writeText(text); true } },
    )
    val demo = "--demo" in args
    val strings = Strings(if ("--lang=tr" in args) true else if ("--lang=en" in args) false else systemLanguage() == "tr")
    val icon = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use { Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }

    application {
        val scope = rememberCoroutineScope()
        val model = remember { AppModel(repo, scope) }
        // While the window is open, sources are checked every minute; each reads only when it is due.
        LaunchedEffect(Unit) {
            if (demo) Demo.run(model, strings)
            while (true) {
                runCatching { repo.refresh(onlyDue = true) }
                delay(60_000)
            }
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = strings.appName,
            icon = icon?.let { BitmapPainter(it) },
            state = rememberWindowState(width = 1180.dp, height = 820.dp),
        ) { App(model, strings) }
    }
}

actual val inAppVideo: Boolean = false

@androidx.compose.runtime.Composable
actual fun YouTubeView(videoId: String, modifier: androidx.compose.ui.Modifier, onEnded: () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier)
}
