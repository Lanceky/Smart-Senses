package com.lanceky.smartsenses.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanceky.smartsenses.R
import com.lanceky.smartsenses.SmartSensesApp
import com.lanceky.smartsenses.data.Event
import com.lanceky.smartsenses.data.EventKind
import com.lanceky.smartsenses.data.Settings
import com.lanceky.smartsenses.data.Signal
import com.lanceky.smartsenses.monitor.EarsStatus
import com.lanceky.smartsenses.monitor.Live
import com.lanceky.smartsenses.monitor.LiveStatus
import com.lanceky.smartsenses.monitor.MonitoringService
import com.lanceky.smartsenses.monitor.toConfig
import com.lanceky.smartsenses.monitor.toEyesConfig
import com.lanceky.smartsenses.permissions.Permissions
import com.lanceky.smartsenses.permissions.rememberPermissionStatus
import com.lanceky.smartsenses.rules.EarsConfig
import com.lanceky.smartsenses.rules.MINUTE_MS
import com.lanceky.smartsenses.rules.Rule
import com.lanceky.smartsenses.rules.Severity
import com.lanceky.smartsenses.ui.components.CapsLabel
import com.lanceky.smartsenses.ui.components.ConsoleButton
import com.lanceky.smartsenses.ui.components.ConsolePanel
import com.lanceky.smartsenses.ui.components.ConsoleText
import com.lanceky.smartsenses.ui.components.StatusDot
import com.lanceky.smartsenses.ui.components.VolumeBar
import com.lanceky.smartsenses.ui.components.color
import com.lanceky.smartsenses.ui.components.glyph
import com.lanceky.smartsenses.ui.theme.Amber
import com.lanceky.smartsenses.ui.theme.Border
import com.lanceky.smartsenses.ui.theme.Neon
import com.lanceky.smartsenses.ui.theme.Red
import com.lanceky.smartsenses.ui.theme.TextDim
import com.lanceky.smartsenses.ui.theme.TextMain
import com.lanceky.smartsenses.rules.Patterns
import com.lanceky.smartsenses.rules.VolumeTrend
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(settings: Settings) {
    val context = LocalContext.current
    val app = context.applicationContext as SmartSensesApp
    val live by LiveStatus.state.collectAsStateWithLifecycle()
    val events by remember { app.db.events().observeRecent(8) }.collectAsStateWithLifecycle(emptyList())
    val permissions = rememberPermissionStatus()
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
        if (!permissions.overlay) {
            Notice("WARNING CARD: UNAVAILABLE · Appear on top is off") {
                context.startActivity(Permissions.overlaySettingsIntent(context))
            }
        }
        if (!permissions.notifications) {
            Notice("NOTIFICATIONS: MISSING · monitoring runs without its notice") {
                context.startActivity(Permissions.notificationSettingsIntent(context))
            }
        }
        EarsCard(settings, live)
        EyesCard(settings, live) { scope.launch { app.settingsStore.setEyesEnabled(true) } }
        ComingLater("DEVICE", "temperature", Modifier.fillMaxWidth())
        PatternsPanel(app, settings)
        SystemLog(events)
    }
}

@Composable
private fun TopBar(settings: Settings, live: Live, onStart: () -> Unit) {
    val active = settings.monitoringEnabled && live.running
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = "Smart-Senses logo", modifier = Modifier.size(40.dp))
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
        val snoozed = settings.snoozeUntil > System.currentTimeMillis()
        if (settings.demoMode || snoozed) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (settings.demoMode) Chip("DEMO MODE", Amber)
                if (snoozed) Chip("SNOOZED UNTIL ${formatClock(settings.snoozeUntil, short = true)}", TextDim)
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    CapsLabel(text, Modifier.border(1.dp, color, RoundedCornerShape(3.dp)).padding(horizontal = 8.dp, vertical = 3.dp), color = color, bold = true)
}

@Composable
private fun Notice(text: String, onFix: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().border(1.dp, Amber.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapsLabel("▲ $text", Modifier.weight(1f), color = Amber)
        Spacer(Modifier.width(8.dp))
        ConsoleButton("fix", onFix, color = Amber)
    }
}

@Composable
private fun EarsCard(settings: Settings, live: Live) {
    val running = settings.monitoringEnabled && live.running
    val config = settings.toConfig()
    ConsolePanel(title = "EARS", accent = Neon, glow = Neon, trailing = { CapsLabel("headphone volume") }) {
        val output = live.output
        when {
            !running -> CapsLabel("HEADPHONES: —")
            output != null -> CapsLabel("HEADPHONES: CONNECTED (${output.name})", color = Neon)
            else -> CapsLabel("HEADPHONES: NOT CONNECTED (speaker never counts)")
        }
        Spacer(Modifier.height(6.dp))
        val high = running && live.volumePercent >= settings.thresholdPercent
        val volumeColor = when {
            !high -> Neon
            live.volumePercent >= EarsConfig.VERY_HIGH_PERCENT -> Red
            else -> Amber
        }
        Row(verticalAlignment = Alignment.Bottom) {
            CapsLabel("VOLUME", Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (running) "${live.volumePercent}%" else "—",
                color = volumeColor,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        VolumeBar(if (running) live.volumePercent else 0, settings.thresholdPercent, volumeColor)
        ConsoleText("threshold ${settings.thresholdPercent}%", color = TextDim, size = 11.sp)
        Spacer(Modifier.height(6.dp))
        when {
            !running -> CapsLabel("PLAYBACK: —")
            live.playing -> CapsLabel("PLAYING", color = Neon)
            else -> CapsLabel("PAUSED: timers hold")
        }
        if (running) {
            if (live.output != null) CapsLabel("LISTENING: ${live.sessionListeningMs / MINUTE_MS} min")
            CapsLabel("TODAY ABOVE ${settings.thresholdPercent}%: ${live.todayHighMs / MINUTE_MS} min")
        }
        Spacer(Modifier.height(8.dp))
        when {
            !running -> ConsoleText("> monitoring is off: no readings_", color = TextDim)
            live.status == EarsStatus.NORMAL -> CapsLabel("● STATUS: NORMAL", color = Neon, bold = true)
            else -> {
                CapsLabel(
                    "▲ HIGH VOLUME: ${mmss(live.continuousHighMs)} / ${mmss(config.continuousMs)}",
                    color = Amber, bold = true,
                )
                if (live.status == EarsStatus.VERY_HIGH) {
                    CapsLabel(
                        "■ VERY HIGH: ${mmss(live.veryHighMs)} / ${mmss(EarsConfig.VERY_HIGH_MINUTES * MINUTE_MS)}",
                        color = Red, bold = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun EyesCard(settings: Settings, live: Live, onTurnOn: () -> Unit) {
    if (!settings.eyesEnabled) {
        Row(
            Modifier.fillMaxWidth().border(1.dp, Border.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                CapsLabel("EYES · OFF")
                Spacer(Modifier.height(2.dp))
                ConsoleText("bright screen in a dim room", color = TextDim.copy(alpha = 0.7f), size = 12.sp)
            }
            ConsoleButton("turn on", onTurnOn)
        }
        return
    }
    val running = settings.monitoringEnabled && live.running
    val config = settings.toEyesConfig()
    ConsolePanel(title = "EYES", accent = Neon, glow = Neon, trailing = { CapsLabel("screen brightness") }) {
        if (!running) {
            ConsoleText("> monitoring is off: no readings_", color = TextDim)
            return@ConsolePanel
        }
        val bright = live.brightnessPercent >= config.brightnessPercent
        val lux = live.lux
        val dim = lux != null && lux < config.dimLux
        val color = if (bright && dim) Amber else Neon
        Row(verticalAlignment = Alignment.Bottom) {
            CapsLabel("BRIGHTNESS", Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.width(10.dp))
            Text("${live.brightnessPercent}%", color = color, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(10.dp))
            CapsLabel(if (live.autoBrightness) "AUTO" else "MANUAL", Modifier.padding(bottom = 8.dp))
        }
        VolumeBar(live.brightnessPercent, config.brightnessPercent, color)
        ConsoleText("threshold ${config.brightnessPercent}%", color = TextDim, size = 11.sp)
        Spacer(Modifier.height(6.dp))
        when {
            !live.lightSensor -> CapsLabel("ROOM LIGHT: NO SENSOR · Eyes can't tell a dim room", color = Amber)
            lux == null -> CapsLabel("ROOM LIGHT: waiting for the light sensor")
            dim -> CapsLabel("ROOM LIGHT: ${lux.toInt()} LUX · DIM (under ${config.dimLux})", color = Amber)
            else -> CapsLabel("ROOM LIGHT: ${lux.toInt()} LUX · LIT", color = Neon)
        }
        Spacer(Modifier.height(8.dp))
        if (live.eyesTiming) {
            CapsLabel("▲ BRIGHT IN DIM ROOM: ${mmss(live.brightInDimMs)} / ${mmss(config.warnMs)}", color = Amber, bold = true)
        } else {
            CapsLabel("● STATUS: NORMAL", color = Neon, bold = true)
        }
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

/** Rule-based patterns from recorded readings only. */
@Composable
private fun PatternsPanel(app: SmartSensesApp, settings: Settings) {
    val today = remember { LocalDate.now() }
    val startOfToday = remember { today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val stats by remember { app.db.readings().observeDailyStats(today.toEpochDay() - 30) }.collectAsStateWithLifecycle(null)
    val stretches by remember { app.db.events().countKindSince(EventKind.HIGH_VOLUME_STARTED, startOfToday) }.collectAsStateWithLifecycle(0)
    val days = stats ?: return
    val highToday = days.firstOrNull { it.epochDay == today.toEpochDay() }?.highMs ?: 0L
    val trend = Patterns.volumeTrend(days)
    ConsolePanel(title = "PATTERNS") {
        ConsoleText("> ${Patterns.todayText(stretches, highToday, settings.thresholdPercent)}", color = TextMain, size = 12.sp)
        Spacer(Modifier.height(4.dp))
        val up = trend is VolumeTrend.Up
        ConsoleText(
            "${if (up) "▲" else ">"} ${Patterns.trendText(trend)}",
            color = if (up) Amber else TextDim,
            size = 12.sp,
        )
    }
}

@Composable
private fun SystemLog(events: List<Event>) {
    ConsolePanel(title = "SYSTEM LOG") {
        if (events.isEmpty()) ConsoleText("> no events yet_", color = TextDim, size = 12.sp)
        events.forEach { e ->
            val severity = e.severity?.let { runCatching { Severity.valueOf(it) }.getOrNull() }
            val isWarning = e.kind == EventKind.WARNING && severity != null
            ConsoleText(
                "> ${formatClock(e.time)}  ${if (isWarning) warningLine(e, severity!!) else e.message}${if (e.demoMode) " [DEMO]" else ""}",
                color = if (isWarning) severity!!.color() else TextDim,
                size = 12.sp,
            )
        }
    }
}

private fun warningLine(e: Event, severity: Severity): String {
    val minutes = (e.durationMs ?: 0) / MINUTE_MS
    if (e.signal == Signal.EYES) return "WARNING ${severity.glyph()} EYES ${e.brightnessPercent}% in ${e.lux} lux for $minutes min"
    return if (e.rule == Rule.DAILY.name) "WARNING ${severity.glyph()} EARS $minutes min loud today"
    else "WARNING ${severity.glyph()} EARS ${e.volumePercent}% for $minutes min"
}

fun mmss(ms: Long): String {
    val total = ms / 1000
    return "%02d:%02d".format(total / 60, total % 60)
}

private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")
private val shortClock = DateTimeFormatter.ofPattern("HH:mm")

fun formatClock(epochMs: Long, short: Boolean = false): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(if (short) shortClock else clock)
