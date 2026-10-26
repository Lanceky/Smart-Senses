package com.lanceky.smartsenses.ui.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanceky.smartsenses.SmartSensesApp
import com.lanceky.smartsenses.data.Event
import com.lanceky.smartsenses.data.EventKind
import com.lanceky.smartsenses.data.Settings
import com.lanceky.smartsenses.monitor.Live
import com.lanceky.smartsenses.monitor.LiveStatus
import com.lanceky.smartsenses.monitor.MonitoringService
import com.lanceky.smartsenses.rules.Severity
import com.lanceky.smartsenses.ui.components.CapsLabel
import com.lanceky.smartsenses.ui.components.ConsoleButton
import com.lanceky.smartsenses.ui.components.ConsolePanel
import com.lanceky.smartsenses.ui.components.ConsoleText
import com.lanceky.smartsenses.ui.components.StatusDot
import com.lanceky.smartsenses.ui.components.VolumeBar
import com.lanceky.smartsenses.ui.components.color
import com.lanceky.smartsenses.ui.theme.Amber
import com.lanceky.smartsenses.ui.theme.Border
import com.lanceky.smartsenses.ui.theme.Neon
import com.lanceky.smartsenses.ui.theme.TextDim
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(settings: Settings) {
    val context = LocalContext.current
    val app = context.applicationContext as SmartSensesApp
    val live by LiveStatus.state.collectAsStateWithLifecycle()
    val events by remember { app.db.events().observeRecent(8) }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val start: () -> Unit = {
        scope.launch {
            app.settingsStore.setMonitoringEnabled(true)
            MonitoringService.start(context, MonitoringService.REASON_USER)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBar(settings, live, start)
        EarsCard(settings, live)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ComingLater("EYES", "screen brightness", Modifier.weight(1f))
            ComingLater("DEVICE", "temperature", Modifier.weight(1f))
        }
        SystemLog(events)
    }
}

@Composable
private fun TopBar(settings: Settings, live: Live, onStart: () -> Unit) {
    val active = settings.monitoringEnabled && live.running
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("◉", color = Neon, fontSize = 26.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("SMART-SENSES", color = Neon, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    active -> { StatusDot(Neon, pulsing = true); Spacer(Modifier.width(6.dp)); CapsLabel("MONITORING: ACTIVE", color = Neon) }
                    settings.monitoringEnabled -> { StatusDot(Amber, pulsing = false); Spacer(Modifier.width(6.dp)); CapsLabel("MONITORING: STOPPED", color = Amber) }
                    else -> { StatusDot(TextDim, pulsing = false); Spacer(Modifier.width(6.dp)); CapsLabel("MONITORING: OFF") }
                }
            }
        }
        when {
            active -> Unit
            settings.monitoringEnabled -> ConsoleButton("restart", onStart, color = Amber)
            else -> ConsoleButton("start", onStart, filled = true)
        }
    }
}

@Composable
private fun EarsCard(settings: Settings, live: Live) {
    val running = settings.monitoringEnabled && live.running
    ConsolePanel(title = "EARS", accent = Neon, glow = Neon, trailing = { CapsLabel("headphone volume") }) {
        val output = live.output
        when {
            !running -> CapsLabel("HEADPHONES: —")
            output != null -> CapsLabel("HEADPHONES: CONNECTED (${output.name})", color = Neon)
            else -> CapsLabel("HEADPHONES: NOT CONNECTED (speaker never counts)")
        }
        Spacer(Modifier.height(6.dp))
        val high = running && live.volumePercent >= settings.thresholdPercent
        Row(verticalAlignment = Alignment.Bottom) {
            CapsLabel("VOLUME", Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (running) "${live.volumePercent}%" else "—",
                color = if (high) Amber else Neon,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        VolumeBar(if (running) live.volumePercent else 0, settings.thresholdPercent, if (high) Amber else Neon)
        ConsoleText("threshold ${settings.thresholdPercent}%", color = TextDim, size = 11.sp)
        Spacer(Modifier.height(6.dp))
        when {
            !running -> CapsLabel("PLAYBACK: —")
            live.playing -> CapsLabel("PLAYING", color = Neon)
            else -> CapsLabel("PAUSED: timers hold")
        }
        Spacer(Modifier.height(6.dp))
        if (running) CapsLabel("● STATUS: NORMAL", color = Neon, bold = true)
        else ConsoleText("> monitoring is off: no readings_", color = TextDim)
    }
}

@Composable
private fun ComingLater(title: String, what: String, modifier: Modifier = Modifier) {
    Column(modifier.border(1.dp, Border.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(10.dp)) {
        CapsLabel("$title · COMING LATER")
        Spacer(Modifier.height(2.dp))
        ConsoleText(what, color = TextDim.copy(alpha = 0.7f), size = 12.sp)
    }
}

@Composable
private fun SystemLog(events: List<Event>) {
    ConsolePanel(title = "SYSTEM LOG") {
        if (events.isEmpty()) ConsoleText("> no events yet_", color = TextDim, size = 12.sp)
        events.forEach { e ->
            val severity = e.severity?.let { runCatching { Severity.valueOf(it) }.getOrNull() }
            ConsoleText(
                "> ${formatClock(e.time)}  ${e.message}${if (e.demoMode) " [DEMO]" else ""}",
                color = if (e.kind == EventKind.WARNING && severity != null) severity.color() else TextDim,
                size = 12.sp,
            )
        }
    }
}

private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

fun formatClock(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(clock)
