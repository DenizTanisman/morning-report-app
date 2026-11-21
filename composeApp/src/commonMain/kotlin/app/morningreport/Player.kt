package app.morningreport

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The 11-character id in a YouTube watch / shorts / youtu.be link, or null. */
fun youtubeId(link: String): String? =
    Regex("(?:v=|/shorts/|youtu\\.be/|/embed/|/live/)([A-Za-z0-9_-]{11})").find(link)?.groupValues?.get(1)

/**
 * Hosts [content] and opens [item] over it as a dialog (Magic UI's HeroVideoDialog, "from-center"): the video grows
 * from half size and fades in on a spring (stiffness 300, damping 30), the page behind is dimmed to 50 % black and
 * blurred, and closing runs the same way back. A tap outside the video closes it. The dialog stays composed until the
 * closing animation ends, so the player is torn down only then.
 */
@Composable
fun PlayerDialog(item: Item?, sourceName: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf<Item?>(null) }
    val pop = remember { Animatable(0f) }
    val dim = remember { Animatable(0f) }
    LaunchedEffect(item) {
        coroutineScope {
            if (item != null) {
                shown = item
                launch { dim.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
                launch { pop.animateTo(1f, MODAL_SPRING) }
            } else if (shown != null) {
                launch { dim.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
                launch { pop.animateTo(0f, MODAL_SPRING) }
            }
        }
        if (item == null) shown = null
    }
    Box(Modifier.fillMaxSize()) {
        val d = dim.value
        Box(Modifier.fillMaxSize().then(if (d > 0.001f) Modifier.blur((12 * d).dp) else Modifier)) { content() }
        val open = shown ?: return@Box
        val id = youtubeId(open.link) ?: return@Box
        // The player is put in once the frame has grown: creating a WebView blocks the UI thread for a moment,
        // which would otherwise swallow the opening animation.
        var playerOn by remember(open.id) { mutableStateOf(false) }
        LaunchedEffect(open.id) { snapshotFlow { pop.value }.first { it > 0.96f }; playerOn = true }
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = d }.background(Color.Black.copy(alpha = 0.5f))
                .clickable(remember { MutableInteractionSource() }, null) { if (item != null) onClose() },
        )
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            // Room for the close button above and the title below, also in landscape.
            val fit = ((maxHeight - 170.dp) * (16f / 9f)).coerceAtLeast(160.dp)
            val p = pop.value
            val uri = LocalUriHandler.current
            Column(
                Modifier.widthIn(max = minOf(fit, 1100.dp)).fillMaxWidth()
                    .graphicsLayer { val sc = 0.5f + 0.5f * p; scaleX = sc; scaleY = sc; alpha = p.coerceIn(0f, 1f) }
                    .pointerInput(Unit) { detectTapGestures { } },
                horizontalAlignment = Alignment.End,
            ) {
                CloseDisc { if (item != null) onClose() }
                Spacer(Modifier.height(20.dp))
                val shape = RoundedCornerShape(16.dp)
                val frame = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).border(2.dp, Color.White, shape).background(Color.Black)
                if (playerOn) YouTubeView(id, frame, onEnded = { if (item != null) onClose() })
                else Box(frame) { RemoteImage(open.thumbnail?.let(::betterThumb), Color.Black, Modifier.fillMaxSize()) }
                Column(Modifier.fillMaxWidth().padding(top = 14.dp, start = 4.dp, end = 4.dp)) {
                    Text(open.title, style = GlassType.headline, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(sourceName, style = GlassType.footnote, color = Color.White.copy(alpha = 0.7f))
                    Text(LocalStrings.current.openInYoutube + " ↗", style = GlassType.footnote, color = Color.White,
                        modifier = Modifier.padding(top = 10.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
                            .clickable { runCatching { uri.openUri(open.link) } }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
    }
}

/** motion's `{ type: "spring", damping: 30, stiffness: 300 }` with mass 1: ratio = 30 / (2·√300) ≈ 0.866. */
private val MODAL_SPRING = spring<Float>(dampingRatio = 0.866f, stiffness = 300f, visibilityThreshold = 0.002f)

/** Magic UI's close button: a round dimmed disc with a thin ring and a white ×. */
@Composable
private fun CloseDisc(onClose: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF171717).copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) { DuoIconView(Duo.Close, 18.dp, line = Color.White, accent = Color.White, backAlpha = 0f) }
}
