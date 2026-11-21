package app.morningreport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A handful of line icons drawn on a 24-unit grid, so the app needs no icon library. */
enum class Icon { Refresh, Tune, Plus, Close, Play, Feed, More, Clock, Sun }

@Composable
fun LineIcon(icon: Icon, color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), stroke.width, StrokeCap.Round)
        when (icon) {
            Icon.Refresh -> {
                drawArc(color, -60f, 300f, false, p(4f, 4f), Size(16 * u, 16 * u), style = stroke)
                line(18f, 3.5f, 18.1f, 8.2f); line(18.1f, 8.2f, 13.6f, 8.0f)
            }
            Icon.Tune -> {
                line(4f, 7f, 20f, 7f); line(4f, 17f, 20f, 17f)
                drawCircle(color, 2.4f * u, p(9f, 7f)); drawCircle(color, 2.4f * u, p(15f, 17f))
            }
            Icon.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            Icon.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Icon.Play -> drawPath(Path().apply { moveTo(8f * u, 5f * u); lineTo(19f * u, 12f * u); lineTo(8f * u, 19f * u); close() }, color)
            Icon.Feed -> {
                drawCircle(color, 1.8f * u, p(6f, 18f))
                drawArc(color, -90f, 90f, false, p(-2f, 6f), Size(16 * u, 16 * u), style = stroke)
                drawArc(color, -90f, 90f, false, p(-8f, 0f), Size(28 * u, 28 * u), style = stroke)
            }
            Icon.More -> listOf(6f, 12f, 18f).forEach { drawCircle(color, 1.7f * u, p(it, 12f)) }
            Icon.Clock -> {
                drawCircle(color, 8f * u, p(12f, 12f), style = stroke)
                line(12f, 12f, 12f, 7.5f); line(12f, 12f, 15f, 14f)
            }
            Icon.Sun -> sun(this, u, color, stroke)
        }
    }
}

private fun sun(d: DrawScope, u: Float, color: Color, stroke: Stroke) = with(d) {
    // A sun half above the horizon: the app's mark.
    drawArc(color, 180f, 180f, false, Offset(6 * u, 9 * u), Size(12 * u, 12 * u), style = stroke)
    drawLine(color, Offset(3 * u, 15 * u), Offset(21 * u, 15 * u), stroke.width, StrokeCap.Round)
    listOf(-150.0, -120.0, -90.0, -60.0, -30.0).forEach { a ->
        val r = a * kotlin.math.PI / 180.0
        val c = Offset(12 * u, 15 * u)
        drawLine(color, c + Offset((8.5 * u * kotlin.math.cos(r)).toFloat(), (8.5 * u * kotlin.math.sin(r)).toFloat()),
            c + Offset((11 * u * kotlin.math.cos(r)).toFloat(), (11 * u * kotlin.math.sin(r)).toFloat()), stroke.width, StrokeCap.Round)
    }
}
