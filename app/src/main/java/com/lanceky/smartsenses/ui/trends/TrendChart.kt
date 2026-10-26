package com.lanceky.smartsenses.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanceky.smartsenses.ui.theme.Amber
import com.lanceky.smartsenses.ui.theme.Border
import com.lanceky.smartsenses.ui.theme.Neon
import com.lanceky.smartsenses.ui.theme.TextDim
import kotlin.math.abs

/** x is 0..1 across the window; value is 0–100%; breakBefore starts a new line segment (a pause or the screen off). */
data class ChartPoint(val x: Float, val value: Float, val breakBefore: Boolean = false)

/** Hand-drawn volume or brightness chart: 0–100% axis, dashed amber threshold, neon line. No chart library. */
@Composable
fun TrendChart(
    points: List<ChartPoint>,
    thresholdPercent: Int,
    xLabels: List<Pair<Float, String>>,
    modifier: Modifier = Modifier,
    dots: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    Canvas(modifier.fillMaxWidth().height(200.dp)) {
        val left = 30.dp.toPx()
        val right = size.width - 6.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        fun xOf(f: Float) = left + f.coerceIn(0f, 1f) * (right - left)
        fun yOf(v: Float) = bottom - v.coerceIn(0f, 100f) / 100f * (bottom - top)

        // Axis and grid: 0, 50, 100 (skipping any that would collide with the threshold label).
        listOf(0, 50, 100).forEach { v ->
            drawLine(Border.copy(alpha = if (v == 0) 1f else 0.45f), Offset(left, yOf(v.toFloat())), Offset(right, yOf(v.toFloat())), 1.dp.toPx())
            if (abs(v - thresholdPercent) >= 10) yLabel(measurer, "$v", labelStyle, left, yOf(v.toFloat()))
        }
        drawLine(Border, Offset(left, top), Offset(left, bottom), 1.dp.toPx())

        val ty = yOf(thresholdPercent.toFloat())
        drawLine(Amber, Offset(left, ty), Offset(right, ty), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
        yLabel(measurer, "$thresholdPercent", labelStyle.copy(color = Amber), left, ty)

        val path = Path()
        points.forEachIndexed { i, p ->
            val o = Offset(xOf(p.x), yOf(p.value))
            if (i == 0 || p.breakBefore) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(path, Neon, style = Stroke(width = 2.dp.toPx()))
        if (dots) points.forEach { drawCircle(Neon, 3.5.dp.toPx(), Offset(xOf(it.x), yOf(it.value))) }

        xLabels.forEach { (f, text) ->
            val layout = measurer.measure(text, labelStyle)
            val x = (xOf(f) - layout.size.width / 2f).coerceIn(left - 4.dp.toPx(), size.width - layout.size.width.toFloat())
            drawText(layout, topLeft = Offset(x, bottom + 4.dp.toPx()))
        }
    }
}

private fun DrawScope.yLabel(measurer: TextMeasurer, text: String, style: TextStyle, left: Float, y: Float) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(left - layout.size.width - 4.dp.toPx(), y - layout.size.height / 2f))
}

/** Minutes over the line per day or week, as small console bars. Many bars: no per-bar numbers, and blank labels are skipped. */
@Composable
fun MinutesBars(days: List<Pair<String, Long>>, modifier: Modifier = Modifier, color: Color = Amber, showValues: Boolean = true) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    val max = (days.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Canvas(modifier.fillMaxWidth().height(86.dp)) {
        val left = 30.dp.toPx()
        val colW = (size.width - left) / days.size.coerceAtLeast(1)
        val base = size.height - 18.dp.toPx()
        val maxH = base - 16.dp.toPx()
        days.forEachIndexed { i, (label, minutes) ->
            val cx = left + colW * (i + 0.5f)
            val h = if (minutes == 0L) 1.dp.toPx() else (minutes.toFloat() / max * maxH).coerceAtLeast(3.dp.toPx())
            val w = colW * 0.45f
            drawRect(if (minutes == 0L) Border else color, Offset(cx - w / 2, base - h), androidx.compose.ui.geometry.Size(w, h))
            if (showValues) {
                val v = measurer.measure("$minutes", style.copy(color = if (minutes == 0L) TextDim else color))
                drawText(v, topLeft = Offset(cx - v.size.width / 2f, base - h - v.size.height - 2.dp.toPx()))
            }
            if (label.isNotEmpty()) {
                val l = measurer.measure(label, style)
                val x = (cx - l.size.width / 2f).coerceIn(0f, size.width - l.size.width)
                drawText(l, topLeft = Offset(x, base + 3.dp.toPx()))
            }
        }
    }
}
