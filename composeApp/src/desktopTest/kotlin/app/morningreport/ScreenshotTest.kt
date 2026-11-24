package app.morningreport

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders the desktop window off screen for the README. Skipped unless SHOT_DATA (a data.json) and SHOT_OUT are set:
 *   SHOT_DATA=… SHOT_OUT=docs/desktop.png ./gradlew :composeApp:desktopTest --tests '*ScreenshotTest*'
 * SHOT_REFRESH=1 reads every source in the data from the network first (a data.json with sources only is enough).
 */
class ScreenshotTest {
    @Test fun desktop() {
        val data = System.getenv("SHOT_DATA") ?: return
        val out = System.getenv("SHOT_OUT") ?: return
        val live = System.getenv("SHOT_REFRESH") == "1"
        val repo = if (live) Repository(load = { File(data).readText() }, save = {})
            else Repository(load = { File(data).readText() }, save = {}, fetcher = { null })
        if (live) runBlocking { repo.refresh(onlyDue = false) }
        val model = AppModel(repo, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        System.getenv("SHOT_SECTION")?.let { t -> model.selected = repo.data.value.sections.firstOrNull { it.title == t }?.id ?: model.selected }
        val scene = ImageComposeScene(1280 * 2, 820 * 2, Density(2f)) { App(model, Strings(System.getenv("SHOT_LANG") == "tr")) }
        var t = 0L
        // Let thumbnails arrive from the network.
        repeat(80) { scene.render(t); t += 100_000_000; Thread.sleep(100) }
        val img = scene.render(t)
        File(out).writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }
}
