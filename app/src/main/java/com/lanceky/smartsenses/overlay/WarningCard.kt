package com.lanceky.smartsenses.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanceky.smartsenses.rules.Warning
import com.lanceky.smartsenses.ui.components.color
import com.lanceky.smartsenses.ui.components.glyph
import com.lanceky.smartsenses.ui.components.label
import com.lanceky.smartsenses.ui.theme.Panel
import com.lanceky.smartsenses.ui.theme.TextDim
import com.lanceky.smartsenses.ui.theme.TextMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

private const val VISIBLE_MS = 5_000L
private val CardShape = RoundedCornerShape(4.dp)

// A buoyant drop: low damping overshoots below the resting spot, bobs back up and settles in about a second.
private val DropSpring = spring<IntOffset>(dampingRatio = 0.5f, stiffness = 120f)

// Extra window height while dropping, so the overshoot isn't clipped by the window's bottom edge.
private val OvershootRoom = 28.dp

/** The floating card: drops from the status bar, stays about 5 s, slides back up. Swipe up to dismiss; tap for details. */
@Composable
fun WarningCard(warning: Warning, onTap: () -> Unit, onGone: () -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    var dragY by remember { mutableFloatStateOf(0f) }
    val dismissPx = with(LocalDensity.current) { 40.dp.toPx() }
    val accent = warning.severity.color()

    LaunchedEffect(Unit) {
        delay(VISIBLE_MS)
        visible.targetState = false
    }
    LaunchedEffect(Unit) {
        // Remove the window only after the slide-out has finished.
        snapshotFlow { !visible.targetState && visible.isIdle && !visible.currentState }.first { it }
        onGone()
    }

    val dropping = visible.targetState && !visible.isIdle
    AnimatedVisibility(
        visibleState = visible,
        modifier = Modifier.padding(bottom = if (dropping) OvershootRoom else 0.dp),
        enter = slideInVertically(DropSpring) { -it },
        exit = slideOutVertically(tween(260)) { -it },
    ) {
        Row(
            Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .offset { IntOffset(0, dragY.roundToInt()) }
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .shadow(14.dp, CardShape, ambientColor = accent, spotColor = accent)
                .clip(CardShape)
                .background(Panel)
                .border(1.5.dp, accent, CardShape)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (dragY < -dismissPx) visible.targetState = false else dragY = 0f },
                        onDragCancel = { dragY = 0f },
                    ) { change, amount ->
                        change.consume()
                        dragY = (dragY + amount).coerceAtMost(0f)
                    }
                }
                .clickable(onClick = onTap),
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${warning.severity.glyph()} ${warning.severity.label()}",
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (warning.demoMode) "SMART-SENSES · DEMO" else "SMART-SENSES",
                        color = TextDim,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(warning.message, color = TextMain, fontSize = 14.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text("tap for details · swipe up to dismiss", color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
