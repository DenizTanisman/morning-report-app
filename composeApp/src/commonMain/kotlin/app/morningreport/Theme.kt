package app.morningreport

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Apple-style look, following the HIG "Materials" page:
 * - Liquid Glass only on the navigation layer (top bar once content scrolls under it, the floating tab bar).
 * - Content sits on thin materials: translucent cards with a hairline edge.
 * - High-contrast labels on top of materials; large bold titles.
 * - Dark: a parliament-blue backdrop lifting to slate at the top with a faint light-blue glow. Light: iOS grouped grey.
 */
@Immutable
data class GlassPalette(
    val dark: Boolean,
    val base: Color,
    val lift: Color,
    val glow: Color?,
    val card: Color,
    val hairline: Color,
    val glassTint: Color,
    val glassEdgeTop: Color,
    val glassEdgeBottom: Color,
    val selection: Color,
    val label: Color,
    val secondary: Color,
    val accent: Color,
    val danger: Color,
    val youtube: Color = Color(0xFFFF3B30),
)

private val DarkGlass = GlassPalette(
    dark = true,
    base = Color(0xFF14213A),
    lift = Color(0xFF2A3F66),
    glow = Color(0xFF5AC8FA),
    card = Color(0xFF22324F).copy(alpha = 0.82f),
    hairline = Color(0xFF9CC9FF).copy(alpha = 0.12f),
    glassTint = Color(0xFF2B3E60).copy(alpha = 0.50f),
    glassEdgeTop = Color.White.copy(alpha = 0.45f),
    glassEdgeBottom = Color(0xFF9CC9FF).copy(alpha = 0.10f),
    selection = Color(0xFF9CC9FF).copy(alpha = 0.16f),
    label = Color(0xFFF5F5F7),
    secondary = Color(0xFFF5F5F7).copy(alpha = 0.58f),
    accent = Color(0xFF64D2FF),
    danger = Color(0xFFFF453A),
)

private val LightGlass = GlassPalette(
    dark = false,
    base = Color(0xFFF2F2F7),
    lift = Color(0xFFFFFFFF),
    glow = null,
    card = Color.White,
    hairline = Color.Black.copy(alpha = 0.06f),
    glassTint = Color.White.copy(alpha = 0.40f),
    glassEdgeTop = Color.White.copy(alpha = 0.95f),
    glassEdgeBottom = Color.White.copy(alpha = 0.25f),
    selection = Color.Black.copy(alpha = 0.07f),
    label = Color(0xFF1C1C1E),
    secondary = Color(0xFF3C3C43).copy(alpha = 0.62f),
    accent = Color(0xFF007AFF),
    danger = Color(0xFFD70015),
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

object GlassType {
    val largeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    val title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
    val headline = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val sectionLabel = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
}

@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) DarkGlass else LightGlass
    val solidCard = p.card.compositeOver(p.base)
    val scheme = if (p.dark) darkColorScheme(
        primary = p.accent, onPrimary = Color(0xFF06233A), background = p.base, surface = p.base, onSurface = p.label, onBackground = p.label,
        onSurfaceVariant = p.secondary, surfaceVariant = solidCard, surfaceContainer = solidCard, surfaceContainerHigh = solidCard,
        surfaceContainerHighest = solidCard, surfaceContainerLow = solidCard, outline = p.hairline.copy(alpha = 0.4f), error = p.danger,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = Color.White, background = p.base, surface = p.base, onSurface = p.label, onBackground = p.label,
        onSurfaceVariant = p.secondary, surfaceVariant = solidCard, surfaceContainer = solidCard, surfaceContainerHigh = solidCard,
        surfaceContainerHighest = solidCard, surfaceContainerLow = solidCard, outline = Color.Black.copy(alpha = 0.18f), error = p.danger,
    )
    CompositionLocalProvider(LocalGlass provides p) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}

/** The backdrop: lighter at the top settling into the base colour, with a faint light-blue glow from the top edge. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val p = LocalGlass.current
    Box(
        modifier.fillMaxSize().background(p.base).drawBehind {
            drawRect(Brush.verticalGradient(listOf(p.lift, p.base), startY = 0f, endY = size.height * 0.6f))
            p.glow?.let { glow ->
                drawRect(Brush.radialGradient(listOf(glow.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width * 0.5f, 0f), radius = size.width * 0.9f))
            }
        },
        content = content,
    )
}

val CardShape = RoundedCornerShape(22.dp)

/** Thin material for content: translucent fill and a hairline edge. Never glass on content. */
@Composable
fun Modifier.contentCard(shape: Shape = CardShape): Modifier {
    val p = LocalGlass.current
    return this.clip(shape).background(p.card).border(0.5.dp, p.hairline, shape)
}

/**
 * A light running around a card's edge: a sweep gradient whose bright head leads a fading tail (a comet), drawn as
 * a soft halo of widening, fading strokes behind the card and a crisp line over its edge. The halo reaches about
 * [CometRoom] past the edge, so nothing around the card may clip there. Only animates while [on].
 */
@Composable
fun Modifier.cometLight(on: Boolean, color: Color, radius: Dp = 22.dp): Modifier {
    if (!on) return this
    val turn by rememberInfiniteTransition(label = "comet").animateFloat(
        0f, 360f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "angle",
    )
    return this.drawWithContent {
        val brush = rotatedBrush(color, turn, size)
        val r = radius.toPx()
        val inset = 0.9.dp.toPx()
        val at = Offset(inset, inset)
        val box = Size(size.width - 2 * inset, size.height - 2 * inset)
        // Halo: a few soft strokes, wider and fainter outward (a portable stand-in for a blur mask).
        listOf(10.dp to 0.10f, 7.dp to 0.16f, 4.5.dp to 0.24f).forEach { (w, a) ->
            drawRoundRect(brush, at, box, CornerRadius(r, r), alpha = a, style = Stroke(width = w.toPx()))
        }
        drawContent()
        drawRoundRect(brush, at, box, CornerRadius(r, r), style = Stroke(width = 1.8.dp.toPx()))
    }
}

/** The comet's gradient turned by [deg]: a sweep gradient has no rotation of its own, so its stops are shifted. */
private fun rotatedBrush(color: Color, deg: Float, size: Size): Brush {
    val clear = color.copy(alpha = 0f)
    val shift = ((deg % 360f) + 360f) % 360f / 360f
    val stops = listOf(0f to clear, 0.45f to clear, 0.7f to color.copy(alpha = 0.18f), 0.88f to color.copy(alpha = 0.55f), 0.965f to color, 0.985f to Color.White, 1f to clear)
    // Shift every stop by [shift] around the circle and wrap, keeping the gradient continuous at the seam.
    val moved = stops.map { (s, c) -> ((s + shift) % 1f) to c }.sortedBy { it.first }
    val first = moved.first(); val last = moved.last()
    // Colour at the seam (position 0/1), interpolated between the stops either side of it.
    val span = (1f - last.first) + first.first
    val t = if (span <= 0f) 0f else (1f - last.first) / span
    val seam = lerp(last.second, first.second, t)
    val all = listOf(0f to seam) + moved + listOf(1f to seam)
    return Brush.sweepGradient(*all.toTypedArray(), center = Offset(size.width / 2f, size.height / 2f))
}

/** Room to leave unclipped around a card that may carry [cometLight]. */
val CometRoom = 14.dp

/** Measures the content [by] larger on every side and places it back, so a clip placed after it clips further out. */
fun Modifier.bleed(by: Dp): Modifier = this.layout { measurable, constraints ->
    val g = by.roundToPx()
    val placeable = measurable.measure(constraints.offset(2 * g, 2 * g))
    val w = (placeable.width - 2 * g).coerceIn(constraints.minWidth, constraints.maxWidth)
    val h = (placeable.height - 2 * g).coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(w, h) { placeable.place(-g, -g) }
}

// ---------------------------------------------------------------------------------------------------- icons

/**
 * Two-tone line icons (24×24 grid, rounded 1.8 strokes), in place of emoji: [back] is a soft shape tinted with
 * the accent at low opacity, [front] the line drawing.
 */
class DuoIcon(val back: ImageVector, val front: ImageVector)

private fun vector(name: String, fills: List<String> = emptyList(), strokes: List<String> = emptyList()): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        fills.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
        strokes.forEach {
            addPath(addPathNodes(it), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
    }.build()

private fun duo(name: String, back: List<String>, front: List<String>, frontFills: List<String> = emptyList()) =
    DuoIcon(vector("$name-back", fills = back), vector("$name-front", fills = frontFills, strokes = front))

object Duo {
    val Close = duo("close", emptyList(), listOf("M6,6 L18,18", "M18,6 L6,18"))
    val Plus = duo("plus", listOf("M12,3 a9,9 0 1 1 0,18 a9,9 0 1 1 0,-18 z"), listOf("M12,7.5 v9", "M7.5,12 h9"))
    val More = duo("more", emptyList(), emptyList(), frontFills = listOf(
        "M6,10.4 a1.6,1.6 0 1 1 0,3.2 a1.6,1.6 0 1 1 0,-3.2 z", "M12,10.4 a1.6,1.6 0 1 1 0,3.2 a1.6,1.6 0 1 1 0,-3.2 z",
        "M18,10.4 a1.6,1.6 0 1 1 0,3.2 a1.6,1.6 0 1 1 0,-3.2 z"))
    val Economy = duo("economy", listOf("M3,18 L9,12 L13,16 L21,8 L21,21 L3,21 Z"), listOf("M3,18 L9,12 L13,16 L21,8", "M15.5,8 L21,8 L21,13.5", "M3,21 L21,21"))
    val Tech = duo("tech", listOf("M9.5,9.5 h5 v5 h-5 z"), listOf(
        "M8,6 h8 a2,2 0 0 1 2,2 v8 a2,2 0 0 1 -2,2 h-8 a2,2 0 0 1 -2,-2 v-8 a2,2 0 0 1 2,-2 z",
        "M9,3 v3", "M12,3 v3", "M15,3 v3", "M9,18 v3", "M12,18 v3", "M15,18 v3",
        "M3,9 h3", "M3,12 h3", "M3,15 h3", "M18,9 h3", "M18,12 h3", "M18,15 h3"))
    val Science = duo("science", listOf("M7.4,14.5 h9.2 l2.3,4.1 a1.2,1.2 0 0 1 -1.05,1.8 H6.15 a1.2,1.2 0 0 1 -1.05,-1.8 Z"),
        listOf("M9,3 h6", "M10,3 v6.2 l-5.6,9.9 a1.6,1.6 0 0 0 1.4,2.4 h12.4 a1.6,1.6 0 0 0 1.4,-2.4 L14,9.2 V3"))
    val Agenda = duo("agenda", listOf("M6.5,8 h5.5 v4.5 h-5.5 z"), listOf(
        "M5,4 h11 a1,1 0 0 1 1,1 v14 a1.5,1.5 0 0 0 1.5,1.5 H5.5 A2.5,2.5 0 0 1 3,18 V5 a1,1 0 0 1 1,-1 z",
        "M17,9 h3 a1,1 0 0 1 1,1 v9 a1.5,1.5 0 0 1 -3,0", "M14,8.5 h0.01", "M14,12 h0.01", "M6.5,15.5 h7.5"))
    val Society = duo("society", listOf("M3,20 c0,-3.6 2.7,-6.2 6,-6.2 s6,2.6 6,6.2 z"), listOf(
        "M9,5 a3,3 0 1 1 0,6 a3,3 0 1 1 0,-6 z", "M3,20 c0,-3.6 2.7,-6.2 6,-6.2 s6,2.6 6,6.2",
        "M16,6.2 a2.4,2.4 0 1 1 0,4.8", "M17,14.2 c2.4,0.4 4,2.6 4,5.3"))
    val Games = duo("games", listOf("M12,3 a9,9 0 1 1 0,18 a9,9 0 1 1 0,-18 z"), listOf(
        "M12,3 a9,9 0 1 1 0,18 a9,9 0 1 1 0,-18 z", "M12,3 v18", "M3,12 h18",
        "M5.6,5.6 c2.4,2.6 2.4,10.2 0,12.8", "M18.4,5.6 c-2.4,2.6 -2.4,10.2 0,12.8"))
    val Watch = duo("watch", listOf("M6,5 h12 a4,4 0 0 1 4,4 v6 a4,4 0 0 1 -4,4 h-12 a4,4 0 0 1 -4,-4 v-6 a4,4 0 0 1 4,-4 z"),
        listOf("M6,5 h12 a4,4 0 0 1 4,4 v6 a4,4 0 0 1 -4,4 h-12 a4,4 0 0 1 -4,-4 v-6 a4,4 0 0 1 4,-4 z"), frontFills = listOf("M10,8.8 L15.6,12 L10,15.2 Z"))
    val Learn = duo("learn", listOf("M3,5 h5.5 a3.5,3.5 0 0 1 3.5,3.5 V20 a2.5,2.5 0 0 0 -2.5,-2.5 H3 z"), listOf(
        "M3,5 h5.5 a3.5,3.5 0 0 1 3.5,3.5 V20 a2.5,2.5 0 0 0 -2.5,-2.5 H3 z", "M21,5 h-5.5 a3.5,3.5 0 0 0 -3.5,3.5 V20 a2.5,2.5 0 0 1 2.5,-2.5 H21 z"))
    val Sparkle = duo("sparkle", listOf("M11,3 C11.6,8.2 14.8,11.4 20,12 C14.8,12.6 11.6,15.8 11,21 C10.4,15.8 7.2,12.6 2,12 C7.2,11.4 10.4,8.2 11,3 Z"),
        listOf("M11,3 C11.6,8.2 14.8,11.4 20,12 C14.8,12.6 11.6,15.8 11,21 C10.4,15.8 7.2,12.6 2,12 C7.2,11.4 10.4,8.2 11,3 Z"),
        frontFills = listOf("M19,2.5 C19.2,4.1 19.9,4.8 21.5,5 C19.9,5.2 19.2,5.9 19,7.5 C18.8,5.9 18.1,5.2 16.5,5 C18.1,4.8 18.8,4.1 19,2.5 Z"))
    val Reload = duo("reload", listOf("M12,4 a8,8 0 1 1 0,16 a8,8 0 1 1 0,-16 z"), listOf("M19.42,15 A8,8 0 1 1 17.66,6.34 L21,9.5", "M21,4 v5.5 h-5.5"))
    val Settings = duo("settings", listOf("M7,4.5 a2.5,2.5 0 1 1 0,5 a2.5,2.5 0 1 1 0,-5 z", "M17,14.5 a2.5,2.5 0 1 1 0,5 a2.5,2.5 0 1 1 0,-5 z"), listOf(
        "M3,7 h1.5", "M9.5,7 H21", "M3,17 h11.5", "M19.5,17 H21",
        "M7,4.5 a2.5,2.5 0 1 1 0,5 a2.5,2.5 0 1 1 0,-5 z", "M17,14.5 a2.5,2.5 0 1 1 0,5 a2.5,2.5 0 1 1 0,-5 z"))
    val Feed = duo("feed", listOf("M5,3 h14 a2,2 0 0 1 2,2 v14 a2,2 0 0 1 -2,2 h-14 a2,2 0 0 1 -2,-2 v-14 a2,2 0 0 1 2,-2 z"), listOf(
        "M7,11 a6,6 0 0 1 6,6", "M7,6.5 a10.5,10.5 0 0 1 10.5,10.5"), frontFills = listOf("M7.5,15.2 a1.4,1.4 0 1 1 0,2.8 a1.4,1.4 0 1 1 0,-2.8 z"))
    val Play = duo("play", listOf("M8,5.5 L18.5,12 L8,18.5 Z"), listOf("M8,5.5 L18.5,12 L8,18.5 Z"))
    val Clock = duo("clock", listOf("M12,3 a9,9 0 1 1 0,18 a9,9 0 1 1 0,-18 z"), listOf("M12,3 a9,9 0 1 1 0,18 a9,9 0 1 1 0,-18 z", "M12,7.5 V12 l3,2"))
    val Sun = duo("sun", listOf("M6,16 a6,6 0 0 1 12,0 z"), listOf("M6,16 a6,6 0 0 1 12,0", "M3,16 h18", "M12,6.5 V4", "M5.6,9.4 L3.9,7.6", "M18.4,9.4 L20.1,7.6", "M8.3,7.3 L7.2,5.2", "M15.7,7.3 L16.8,5.2"))

    /** A section's icon from its name, in English or Turkish. */
    fun forSection(title: String): DuoIcon {
        val t = title.lowercase()
        fun has(vararg w: String) = w.any { t.contains(it) }
        return when {
            has("econ", "ekonom", "finan", "para", "borsa", "market", "money", "piyasa", "business", "iş dünya") -> Economy
            has("sport", "spor", "nba", "futbol", "football", "soccer", "basket", "maç", "game", "oyun") -> Games
            has("tech", "tekno", "yapay", "ai", "bilişim", "software", "yazılım", "code", "kod") -> Tech
            has("scien", "bilim", "uzay", "space", "health", "sağlık", "tıp") -> Science
            has("society", "toplum", "people", "insan", "kültür", "culture") -> Society
            has("video", "youtube", "izle", "watch", "film", "dizi") -> Watch
            has("learn", "ders", "eğitim", "course", "kurs", "okul", "school") -> Learn
            has("world", "dünya", "gündem", "news", "haber", "polit", "siyaset", "türkiye", "turkey") -> Agenda
            else -> Sparkle
        }
    }
}

/** Draws a [DuoIcon]: soft accent fill behind, line drawing in front. */
@Composable
fun DuoIconView(icon: DuoIcon, size: Dp, line: Color, accent: Color, modifier: Modifier = Modifier, backAlpha: Float = 0.35f) {
    Box(modifier.size(size)) {
        Image(icon.back, null, Modifier.size(size), colorFilter = ColorFilter.tint(accent.copy(alpha = backAlpha)))
        Image(icon.front, null, Modifier.size(size), colorFilter = ColorFilter.tint(line))
    }
}

/**
 * Open/close mark for collapsible rows, in place of a chevron: closed, the sun waits on the horizon; open, it has
 * risen and spread its rays.
 */
@Composable
fun SunriseToggle(open: Boolean, line: Color, accent: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val t by animateFloatAsState(if (open) 1f else 0f, spring(dampingRatio = 0.75f, stiffness = 260f), label = "sunrise")
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val width = 1.8f * u
        val horizon = 18.5f * u
        val radius = 4f * u
        val sun = Offset(12f * u, (18.5f - 6f * t) * u)
        val color = lerp(line, accent, t.coerceIn(0f, 1f))
        clipRect(bottom = horizon) {
            drawCircle(accent.copy(alpha = 0.35f), radius, sun)
            drawCircle(color, radius, sun, style = Stroke(width))
        }
        drawLine(color, Offset(3f * u, horizon), Offset(21f * u, horizon), width, StrokeCap.Round)
        val rays = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
        if (rays > 0f) for (degrees in 180..360 step 45) {
            val a = degrees * PI / 180.0
            val d = Offset(cos(a).toFloat(), sin(a).toFloat())
            drawLine(color.copy(alpha = rays), sun + d * (6.2f * u), sun + d * ((6.2f + 2f * rays) * u), width, StrokeCap.Round)
        }
    }
}
