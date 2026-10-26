package com.lanceky.smartsenses.ui.history

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanceky.smartsenses.SmartSensesApp
import com.lanceky.smartsenses.data.Event
import com.lanceky.smartsenses.data.ListeningSession
import com.lanceky.smartsenses.data.Signal
import com.lanceky.smartsenses.rules.MINUTE_MS
import com.lanceky.smartsenses.rules.Rule
import com.lanceky.smartsenses.rules.Severity
import com.lanceky.smartsenses.ui.components.CapsLabel
import com.lanceky.smartsenses.ui.components.ConsoleText
import com.lanceky.smartsenses.ui.components.color
import com.lanceky.smartsenses.ui.components.glyph
import com.lanceky.smartsenses.ui.components.label
import com.lanceky.smartsenses.ui.dashboard.formatClock
import com.lanceky.smartsenses.ui.theme.Amber
import com.lanceky.smartsenses.ui.theme.Border
import com.lanceky.smartsenses.ui.theme.Neon
import com.lanceky.smartsenses.ui.theme.TextDim
import com.lanceky.smartsenses.ui.theme.TextMain
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One History line: a warning that fired, or a listening session. */
private sealed interface HistoryRow {
    val time: Long
    val key: String
}

private data class WarningRow(val event: Event) : HistoryRow {
    override val time get() = event.time
    override val key get() = "w${event.id}"
}

private data class SessionRow(val session: ListeningSession) : HistoryRow {
    override val time get() = session.startedAt
    override val key get() = "s${session.id}"
}

/** Warnings and listening sessions together, newest first. Room pushes new rows as they're recorded. */
@Composable
fun HistoryScreen(onOpenWarning: ((Long) -> Unit)? = null) {
    val app = LocalContext.current.applicationContext as SmartSensesApp
    val warnings by remember { app.db.events().observeWarnings() }.collectAsStateWithLifecycle(null)
    val sessions by remember { app.db.sessions().observeAll() }.collectAsStateWithLifecycle(null)
    val loaded = warnings != null && sessions != null
    val rows = remember(warnings, sessions) {
        (warnings.orEmpty().map(::WarningRow) + sessions.orEmpty().map(::SessionRow)).sortedByDescending { it.time }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
        CapsLabel("HISTORY", color = Neon, size = 15.sp, bold = true)
        Spacer(Modifier.height(4.dp))
        ConsoleText("TIME · SIGNAL · READING · DURATION/CONTEXT · SEVERITY", color = TextDim, size = 9.sp, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Border, thickness = 1.dp)
        when {
            !loaded -> Unit
            rows.isEmpty() -> Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ConsoleText("> no events yet_", color = Neon)
                ConsoleText(
                    "Listen through headphones and your sessions appear here. Warnings join them when a card fires.",
                    color = TextDim, size = 12.sp,
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                var lastDay: LocalDate? = null
                rows.forEach { row ->
                    val day = localDay(row.time)
                    if (day != lastDay) {
                        lastDay = day
                        item(key = "d$day") { DayHeader(day) }
                    }
                    item(key = row.key) {
                        when (row) {
                            is WarningRow -> WarningLine(row.event, onOpenWarning)
                            is SessionRow -> SessionLine(row.session)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate) {
    val today = LocalDate.now()
    val label = when (day) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> day.format(dayFormat)
    }
    CapsLabel("── $label ──", Modifier.padding(top = 12.dp, bottom = 4.dp), size = 10.sp)
}

@Composable
private fun WarningLine(e: Event, onOpen: ((Long) -> Unit)?) {
    val severity = e.severity?.let { runCatching { Severity.valueOf(it) }.getOrNull() } ?: Severity.CAUTION
    val minutes = (e.durationMs ?: 0) / MINUTE_MS
    val eyes = e.signal == Signal.EYES
    val context = when {
        eyes -> "in ${e.lux ?: 0} lux for $minutes min"
        e.rule == Rule.DAILY.name -> "$minutes min loud today"
        else -> "high for $minutes min"
    }
    Line(
        time = e.time,
        signal = e.signal,
        reading = "${(if (eyes) e.brightnessPercent else e.volumePercent) ?: 0}%",
        context = listOfNotNull(context, if (eyes) null else e.outputType).joinToString(" · "),
        severity = severity,
        demo = e.demoMode,
        modifier = if (onOpen != null) Modifier.clickable { onOpen(e.id) } else Modifier,
    )
}

@Composable
private fun SessionLine(s: ListeningSession) {
    val severity = runCatching { Severity.valueOf(s.maxSeverity) }.getOrDefault(Severity.NORMAL)
    Line(
        time = s.startedAt,
        signal = Signal.EARS,
        reading = "peak ${s.peakVolumePercent}%",
        context = listOfNotNull("listened ${duration(s.listeningMs)}", s.outputType, if (s.endedAt == null) "ongoing" else null)
            .joinToString(" · "),
        severity = severity,
        demo = false,
    )
}

/** Two console lines: `21:04  EARS  85%  ▲ CAUTION`, then the duration and context underneath. */
@Composable
private fun Line(time: Long, signal: String, reading: String, context: String, severity: Severity, demo: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConsoleText(formatClock(time, short = true), Modifier.width(52.dp), color = TextDim)
            ConsoleText(signal, Modifier.width(48.dp), color = Neon)
            ConsoleText(reading, Modifier.weight(1f), color = TextMain, maxLines = 1)
            ConsoleText("${severity.glyph()} ${severity.label()}", color = severity.color(), size = 12.sp)
        }
        Row(Modifier.padding(start = 52.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            ConsoleText(context, Modifier.weight(1f, fill = false), color = TextDim, size = 12.sp)
            if (demo) {
                Spacer(Modifier.width(8.dp))
                CapsLabel(
                    "demo",
                    Modifier.border(1.dp, Amber, RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
                    color = Amber, size = 9.sp,
                )
            }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

private fun localDay(epochMs: Long): LocalDate = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()

private fun duration(ms: Long): String = if (ms < MINUTE_MS) "${ms / 1000}s" else "${ms / MINUTE_MS} min"
