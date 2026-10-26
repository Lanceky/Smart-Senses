package com.lanceky.smartsenses.ui.insight

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanceky.smartsenses.SmartSensesApp
import com.lanceky.smartsenses.data.Event
import com.lanceky.smartsenses.data.EventKind
import com.lanceky.smartsenses.data.Settings
import com.lanceky.smartsenses.data.Signal
import com.lanceky.smartsenses.rules.EarsConfig
import com.lanceky.smartsenses.rules.MINUTE_MS
import com.lanceky.smartsenses.rules.Rule
import com.lanceky.smartsenses.rules.Severity
import com.lanceky.smartsenses.ui.components.CapsLabel
import com.lanceky.smartsenses.ui.components.ConsoleButton
import com.lanceky.smartsenses.ui.components.ConsolePanel
import com.lanceky.smartsenses.ui.components.ConsoleText
import com.lanceky.smartsenses.ui.components.color
import com.lanceky.smartsenses.ui.components.glyph
import com.lanceky.smartsenses.ui.components.label
import com.lanceky.smartsenses.ui.dashboard.formatClock
import com.lanceky.smartsenses.ui.theme.Amber
import com.lanceky.smartsenses.ui.theme.Neon
import com.lanceky.smartsenses.ui.theme.TextDim
import com.lanceky.smartsenses.ui.theme.TextMain
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val SNOOZE_MS = EarsConfig.SNOOZE_MINUTES * MINUTE_MS

/** Why one warning fired, opened from the card or a History row. Snooze lives here so the card stays uncluttered. */
@Composable
fun InsightScreen(warningId: Long, settings: Settings, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as SmartSensesApp
    val event by remember(warningId) { app.db.events().observe(warningId) }.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CapsLabel("INSIGHT", color = Neon, size = 15.sp, bold = true)
            Spacer(Modifier.weight(1f))
            ConsoleButton("back", onClose, color = TextDim)
        }
        val e = event
        if (e == null || e.kind != EventKind.WARNING) {
            ConsoleText("> warning not found_", color = TextDim)
            return@Column
        }
        val severity = e.severity?.let { runCatching { Severity.valueOf(it) }.getOrNull() } ?: Severity.CAUTION
        val eyes = e.signal == Signal.EYES

        ConsolePanel(title = "${e.signal} · WARNING", accent = severity.color(), glow = severity.color()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConsoleText("${severity.glyph()} ${severity.label()}", color = severity.color(), size = 16.sp)
                if (e.demoMode) {
                    Spacer(Modifier.width(10.dp))
                    CapsLabel(
                        "demo",
                        Modifier.border(1.dp, Amber, RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
                        color = Amber, size = 9.sp,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Fact("RULE", ruleName(e))
            if (eyes) {
                Fact("BRIGHTNESS", "${e.brightnessPercent ?: 0}%")
                Fact("ROOM LIGHT", "${e.lux ?: 0} lux")
            } else {
                Fact("VOLUME", "${e.volumePercent ?: 0}%")
            }
            Fact("HOW LONG", howLong(e))
            Fact("TIME", "${formatClock(e.time)} · ${dayLabel(e.time)}")
            if (!eyes) Fact("OUTPUT", e.outputType ?: "HEADPHONES")
        }

        ConsolePanel(title = "WHY") {
            ConsoleText("> ${why(e, settings)}", color = TextMain)
            if (e.demoMode && (e.rule == Rule.CONTINUOUS.name || e.rule == "BRIGHT_IN_DIM")) {
                Spacer(Modifier.height(6.dp))
                ConsoleText("Demo mode shortened the wait to 1 minute; the rule is otherwise the real one.", color = Amber, size = 12.sp)
            }
        }

        ConsolePanel(title = "NOTE") {
            ConsoleText(
                if (eyes) "Brightness % and room light are phone readings, not an eye-health measurement."
                else "Volume % is an estimate of exposure, not a decibel measurement; headphones differ in loudness.",
                color = TextDim, size = 12.sp,
            )
        }

        val snoozed = settings.snoozeUntil > System.currentTimeMillis()
        if (snoozed) {
            ConsoleButton("snoozed until ${formatClock(settings.snoozeUntil, short = true)}", {}, Modifier.fillMaxWidth(), color = TextDim, enabled = false)
        } else {
            ConsoleButton("snooze 1 hour", {
                scope.launch {
                    val until = System.currentTimeMillis() + SNOOZE_MS
                    app.settingsStore.setSnoozeUntil(until)
                    app.db.events().insert(
                        Event(time = System.currentTimeMillis(), kind = EventKind.SNOOZED, message = "cards snoozed until ${formatClock(until, short = true)}")
                    )
                }
            }, Modifier.fillMaxWidth(), color = Neon)
        }
        ConsoleText("Snooze pauses warning cards only. Monitoring and History keep recording.", color = TextDim, size = 11.sp)
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        ConsoleText(label, Modifier.width(110.dp), color = TextDim, size = 12.sp)
        ConsoleText(value, color = TextMain)
    }
}

private fun ruleName(e: Event): String = when (e.rule) {
    Rule.CONTINUOUS.name -> "HIGH VOLUME, CONTINUOUS"
    Rule.VERY_HIGH.name -> "VERY HIGH VOLUME"
    Rule.DAILY.name -> "DAILY LOUD TIME"
    "BRIGHT_IN_DIM" -> "BRIGHT SCREEN, DIM ROOM"
    else -> e.rule ?: "WARNING"
}

private fun howLong(e: Event): String {
    val ms = e.durationMs ?: 0
    val text = if (ms < MINUTE_MS) "${ms / 1000} s" else "${ms / MINUTE_MS} min"
    return when (e.rule) {
        Rule.DAILY.name -> "$text loud today"
        "BRIGHT_IN_DIM" -> "$text in a dim room"
        else -> "high for $text"
    }
}

private fun why(e: Event, s: Settings): String {
    val minutes = (e.durationMs ?: 0) / MINUTE_MS
    return when (e.rule) {
        Rule.VERY_HIGH.name ->
            "Volume stayed at or above ${EarsConfig.VERY_HIGH_PERCENT}% for $minutes min while audio played through headphones. That's the red rule."
        Rule.DAILY.name ->
            "Today's listening at or above ${s.thresholdPercent}% added up to $minutes min, past your daily limit of ${s.dailyMinutes} min."
        "BRIGHT_IN_DIM" ->
            "Screen brightness stayed at or above ${s.eyesBrightnessPercent}% while the room was darker than ${s.eyesDimLux} lux, for $minutes min."
        else ->
            "Volume stayed at or above ${s.thresholdPercent}% for $minutes min while audio played through headphones. Paused time didn't count."
    }
}

private fun dayLabel(epochMs: Long): String {
    val day = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (day) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> day.toString()
    }
}
