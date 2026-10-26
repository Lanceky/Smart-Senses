package com.lanceky.smartsenses.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanceky.smartsenses.SmartSensesApp
import com.lanceky.smartsenses.data.Reading
import com.lanceky.smartsenses.data.Settings
import com.lanceky.smartsenses.rules.DayStat
import com.lanceky.smartsenses.rules.MINUTE_MS
import com.lanceky.smartsenses.ui.components.CapsLabel
import com.lanceky.smartsenses.ui.components.ConsoleButton
import com.lanceky.smartsenses.ui.components.ConsolePanel
import com.lanceky.smartsenses.ui.components.ConsoleText
import com.lanceky.smartsenses.ui.dashboard.formatClock
import com.lanceky.smartsenses.ui.theme.Amber
import com.lanceky.smartsenses.ui.theme.Neon
import com.lanceky.smartsenses.ui.theme.TextDim
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val MIN_DAY_SPAN_MS = 10 * MINUTE_MS
private const val GAP_MS = 25_000L

@Composable
fun TrendsScreen(settings: Settings) {
    val app = LocalContext.current.applicationContext as SmartSensesApp
    var week by rememberSaveable { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val readings by remember { app.db.readings().observeDay(today.toEpochDay()) }.collectAsStateWithLifecycle(null)
    val stats by remember { app.db.readings().observeDailyStats(today.toEpochDay() - 6) }.collectAsStateWithLifecycle(null)
    val threshold = settings.thresholdPercent

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CapsLabel("TRENDS", color = Neon, size = 15.sp, bold = true)
            Spacer(Modifier.weight(1f))
            ConsoleButton("day", { week = false }, filled = !week)
            Spacer(Modifier.padding(start = 8.dp))
            ConsoleButton("week", { week = true }, filled = week)
        }
        ConsolePanel(title = if (week) "EARS · LAST 7 DAYS" else "EARS · TODAY") {
            if (week) WeekView(stats, today, threshold) else DayView(readings, threshold)
        }
        ConsoleText(
            "Volume is % of the phone's media range while audio played through headphones: an exposure estimate, not decibels. " +
                "Brightness and temperature charts come later.",
            color = TextDim, size = 11.sp,
        )
    }
}

@Composable
private fun DayView(readings: List<Reading>?, threshold: Int) {
    if (readings == null) return
    if (readings.size < 2) return NotEnough()
    val start = readings.first().time - 10_000
    val end = maxOf(System.currentTimeMillis(), start + MIN_DAY_SPAN_MS)
    val span = (end - start).toFloat()
    val points = readings.mapIndexed { i, r ->
        ChartPoint((r.time - start) / span, r.volumePercent.toFloat(), breakBefore = i > 0 && r.time - readings[i - 1].time > GAP_MS)
    }
    val labels = listOf(0f, 0.5f, 1f).map { f -> f to formatClock(start + (span * f).toLong(), short = true) }
    EarsChart(points, threshold, labels)
    Spacer(Modifier.height(8.dp))
    val playing = readings.sumOf { it.playingMs }
    val high = readings.sumOf { it.highMs }
    val avg = if (playing > 0) (readings.sumOf { it.volumePercent.toLong() * it.playingMs }.toDouble() / playing).roundToInt() else 0
    ConsoleText("TODAY ABOVE $threshold%: ${high / MINUTE_MS} min", color = if (high > 0) Amber else TextDim)
    ConsoleText("LISTENED: ${playing / MINUTE_MS} min · AVERAGE VOLUME: $avg%", color = TextDim, size = 12.sp)
}

@Composable
private fun WeekView(stats: List<DayStat>?, today: LocalDate, threshold: Int) {
    if (stats == null) return
    if (stats.sumOf { it.readings } < 2) return NotEnough()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val byDay = stats.associateBy { it.epochDay }
    val points = days.mapIndexedNotNull { i, d ->
        byDay[d.toEpochDay()]?.let { ChartPoint((i + 0.5f) / days.size, it.avgVolume.toFloat()) }
    }
    val labels = days.mapIndexed { i, d -> (i + 0.5f) / days.size to weekday(d) }
    EarsChart(points, threshold, labels, dots = true)
    Spacer(Modifier.height(10.dp))
    CapsLabel("MINUTES AT OR ABOVE $threshold% PER DAY", size = 10.sp)
    MinutesBars(days.map { d -> weekday(d) to (byDay[d.toEpochDay()]?.highMs ?: 0L) / MINUTE_MS })
    val listeningDays = stats.count { it.playingMs > 0 }
    ConsoleText("Points: average volume on each listening day ($listeningDays of 7).", color = TextDim, size = 12.sp)
}

@Composable
private fun NotEnough() {
    Column(Modifier.fillMaxWidth().height(120.dp), verticalArrangement = Arrangement.Center) {
        ConsoleText("> Not enough data yet_", color = Neon)
        ConsoleText("Listen through headphones and readings appear here every 10 seconds.", color = TextDim, size = 12.sp)
    }
}

private fun weekday(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
