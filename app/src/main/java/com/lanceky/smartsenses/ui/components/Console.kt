package com.lanceky.smartsenses.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanceky.smartsenses.rules.Severity
import com.lanceky.smartsenses.ui.theme.Amber
import com.lanceky.smartsenses.ui.theme.Black
import com.lanceky.smartsenses.ui.theme.Border
import com.lanceky.smartsenses.ui.theme.Neon
import com.lanceky.smartsenses.ui.theme.Panel
import com.lanceky.smartsenses.ui.theme.PanelRaised
import com.lanceky.smartsenses.ui.theme.Red
import com.lanceky.smartsenses.ui.theme.TextDim
import com.lanceky.smartsenses.ui.theme.TextMain

private val PanelShape = RoundedCornerShape(4.dp)

/** A console panel: Panel fill, 1 dp border, caps header. [glow] adds the restrained colored shadow. */
@Composable
fun ConsolePanel(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = TextDim,
    glow: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .then(if (glow != null) Modifier.shadow(14.dp, PanelShape, ambientColor = glow, spotColor = glow) else Modifier)
            .background(Panel, PanelShape)
            .border(1.dp, if (glow != null) glow.copy(alpha = 0.55f) else Border, PanelShape)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CapsLabel(title, color = accent)
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
fun CapsLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextDim,
    size: TextUnit = 11.sp,
    bold: Boolean = false,
) {
    Text(
        text.uppercase(),
        modifier,
        color = color,
        fontSize = size,
        letterSpacing = 1.5.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
    )
}

@Composable
fun ConsoleText(text: String, modifier: Modifier = Modifier, color: Color = TextMain, size: TextUnit = 13.sp, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier, color = color, fontSize = size, fontFamily = FontFamily.Monospace, maxLines = maxLines, lineHeight = size * 1.35f)
}

@Composable
fun StatusDot(color: Color, pulsing: Boolean, modifier: Modifier = Modifier) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        transition.animateFloat(1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha").value
    } else 1f
    Box(modifier.size(8.dp).alpha(alpha).background(color, CircleShape))
}

@Composable
fun ConsoleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Neon,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .clip(PanelShape)
            .then(if (filled) Modifier.background(color, PanelShape) else Modifier.border(1.dp, color, PanelShape))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "[ ${text.uppercase()} ]",
            color = if (filled) Black else color,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

/** Thin volume bar with an amber tick at the threshold. */
@Composable
fun VolumeBar(percent: Int, thresholdPercent: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val trackTop = size.height * 0.3f
        val trackHeight = size.height * 0.4f
        drawRect(PanelRaised, topLeft = Offset(0f, trackTop), size = Size(size.width, trackHeight))
        drawRect(color, topLeft = Offset(0f, trackTop), size = Size(size.width * percent.coerceIn(0, 100) / 100f, trackHeight))
        val x = size.width * thresholdPercent.coerceIn(0, 100) / 100f
        drawLine(Amber, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
    }
}

fun Severity.glyph(): String = when (this) {
    Severity.NORMAL -> "●"
    Severity.CAUTION -> "▲"
    Severity.DANGER -> "■"
}

fun Severity.color(): Color = when (this) {
    Severity.NORMAL -> Neon
    Severity.CAUTION -> Amber
    Severity.DANGER -> Red
}

fun Severity.label(): String = when (this) {
    Severity.NORMAL -> "NORMAL"
    Severity.CAUTION -> "CAUTION"
    Severity.DANGER -> "DANGER"
}
