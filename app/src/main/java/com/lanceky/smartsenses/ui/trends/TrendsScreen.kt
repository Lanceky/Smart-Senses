package com.lanceky.smartsenses.ui.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanceky.smartsenses.SmartSensesApp
import com.lanceky.smartsenses.data.Settings
import com.lanceky.smartsenses.rules.DayStat
import com.lanceky.smartsenses.rules.MINUTE_MS
import com.lanceky.smartsenses.rules.TrendBucket
import com.lanceky.smartsenses.rules.TrendBuckets
import com.lanceky.smartsenses.ui.components.CapsLabel
import com.lanceky.smartsenses.ui.components.ConsolePanel
import com.lanceky.smartsenses.ui.components.ConsoleText
import com.lanceky.smartsenses.ui.dashboard.formatClock
import com.lanceky.smartsenses.ui.theme.Amber
import com.lanceky.smartsenses.ui.theme.Black
import com.lanceky.smartsenses.ui.theme.Neon
import com.lanceky.smartsenses.ui.theme.TextDim
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val MIN_DAY_SPAN_MS = 10 * MINUTE_MS
private const val GAP_MS = 25_000L
private val SegmentShape = RoundedCornerShape(4.dp)

private enum class TrendSignal(val label: String) { VOLUME("VOLUME"), BRIGHTNESS("BRIGHTNESS") }

/** Today draws the raw 10-second readings; Week and Month one point per day; 6 Months one point per week. */
private enum class TrendRange(val label: String, val title: String, val buckets: Int, val daysPerBucket: Int) {
    TODAY("TODAY", "TODAY", 1, 1),
    WEEK("WEEK", "LAST 7 DAYS", 7, 1),
    MONTH("MONTH", "LAST 30 DAYS", 30, 1),
    SIX_MONTHS("6 MONTHS", "LAST 6 MONTHS", 26, 7),
}

/** One 10-second reading of either signal: its value, active time (playing or screen on) and time over the line. */
private data class TimedValue(val time: Long, val value: Int, val activeMs: Long, val aboveMs: Long)

/** The words that change between the two signals. */
private class SignalWords(
    val panel: String,
    val threshold: Int,
    val above: String,
    val bars: String,
    val active: String,
    val average: String,
    val activeDay: String,
    val activeTime: String,
    val emptyHint: String,
    val note: String,
)

@Composable
fun TrendsScreen(settings: Settings) {
    val app = LocalContext.current.applicationContext as SmartSensesApp
    var signal by rememberSaveable { mutableStateOf(TrendSignal.VOLUME) }
    var range by rememberSaveable { mutableStateOf(TrendRange.TODAY) }
    val today = remember { LocalDate.now() }
    val day = today.toEpochDay()
    val fromDay = day - TrendRange.SIX_MONTHS.buckets * 7L + 1
    val volumeToday by remember {
        app.db.readings().observeDay(day).map { rows -> rows.map { TimedValue(it.time, it.volumePercent, it.playingMs, it.highMs) } }
    }.collectAsStateWithLifecycle(null)
    val brightnessToday by remember {
        app.db.brightness().observeDay(day).map { rows -> rows.map { TimedValue(it.time, it.brightnessPercent, it.screenOnMs, it.brightInDimMs) } }
    }.collectAsStateWithLifecycle(null)
    val volumeStats by remember { app.db.readings().observeDailyStats(fromDay) }.collectAsStateWithLifecycle(null)
    val brightnessStats by remember { app.db.brightness().observeDailyStats(fromDay) }.collectAsStateWithLifecycle(null)

    val volume = signal == TrendSignal.VOLUME
    val words = if (volume) {
        val t = settings.thresholdPercent
        SignalWords(
            panel = "EARS · VOLUME", threshold = t, above = "ABOVE $t%", bars = "MINUTES AT OR ABOVE $t%",
            active = "LISTENED", average = "AVERAGE VOLUME", activeDay = "listening day", activeTime = "listening time",
            emptyHint = "Listen through headphones and readings appear here every 10 seconds.",
            note = "Volume is % of the phone's media range while audio played through headphones: an exposure estimate, not decibels.",
        )
    } else {
        val t = settings.eyesBrightnessPercent
        SignalWords(
            panel = "EYES · BRIGHTNESS", threshold = t, above = "BRIGHT IN A DIM ROOM", bars = "MINUTES BRIGHT IN A DIM ROOM",
            active = "SCREEN ON", average = "AVERAGE BRIGHTNESS", activeDay = "day with the screen on", activeTime = "screen-on time",
            emptyHint = if (settings.eyesEnabled) "Readings appear here every 10 seconds while the screen is on and unlocked."
            else "Turn on Eyes in Settings to record screen brightness every 10 seconds.",
            note = "Brightness is the screen setting (0–100%) while the screen was on and unlocked. Bright in a dim room means " +
                "at or above $t% with the light sensor under ${settings.eyesDimLux} lux. A comfort nudge, not a medical measure.",
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CapsLabel("TRENDS", color = Neon, size = 15.sp, bold = true)
        Segmented(TrendSignal.entries.map { it to it.label }, signal) { signal = it }
        Segmented(TrendRange.entries.map { it to it.label }, range) { range = it }
        ConsolePanel(title = "${words.panel} · ${range.title}") {
            if (range == TrendRange.TODAY) TodayView(if (volume) volumeToday else brightnessToday, words)
            else RangeView(if (volume) volumeStats else brightnessStats, today, range, words)
        }
        ConsoleText(words.note, color = TextDim, size = 11.sp)
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(SegmentShape).border(1.dp, Neon, SegmentShape)) {
        options.forEachIndexed { i, (value, label) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Neon))
            val on = value == selected
            Box(
                Modifier.weight(1f)
                    .background(if (on) Neon else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(value) })
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, color = if (on) Black else Neon, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun TodayView(readings: List<TimedValue>?, w: SignalWords) {
    if (readings == null) return
    if (readings.size < 2) return NotEnough(w.emptyHint)
    val start = readings.first().time - 10_000
    val end = maxOf(System.currentTimeMillis(), start + MIN_DAY_SPAN_MS)
    val span = (end - start).toFloat()
    val points = readings.mapIndexed { i, r ->
        ChartPoint((r.time - start) / span, r.value.toFloat(), breakBefore = i > 0 && r.time - readings[i - 1].time > GAP_MS)
    }
    val labels = listOf(0f, 0.5f, 1f).map { f -> f to formatClock(start + (span * f).toLong(), short = true) }
    TrendChart(points, w.threshold, labels)
    Spacer(Modifier.height(8.dp))
    val active = readings.sumOf { it.activeMs }
    val above = readings.sumOf { it.aboveMs }
    val avg = if (active > 0) (readings.sumOf { it.value.toLong() * it.activeMs }.toDouble() / active).roundToInt() else 0
    ConsoleText("TODAY ${w.above}: ${above / MINUTE_MS} min", color = if (above > 0) Amber else TextDim)
    ConsoleText("${w.active}: ${active / MINUTE_MS} min · ${w.average}: $avg%", color = TextDim, size = 12.sp)
}

@Composable
private fun RangeView(stats: List<DayStat>?, today: LocalDate, range: TrendRange, w: SignalWords) {
    if (stats == null) return
    val buckets = TrendBuckets.of(stats, today.toEpochDay(), range.buckets, range.daysPerBucket)
    if (buckets.sumOf { it.readings } < 2) return NotEnough(w.emptyHint)
    val n = buckets.size
    fun xOf(i: Int) = (i + 0.5f) / n
    val names = bucketLabels(buckets, range)
    val points = buckets.mapIndexedNotNull { i, b -> b.average?.let { ChartPoint(xOf(i), it.toFloat()) } }
    TrendChart(points, w.threshold, names.mapIndexedNotNull { i, l -> if (l.isEmpty()) null else xOf(i) to l }, dots = true)
    Spacer(Modifier.height(10.dp))
    val weekly = range.daysPerBucket > 1
    CapsLabel("${w.bars} PER ${if (weekly) "WEEK" else "DAY"}", size = 10.sp)
    MinutesBars(buckets.mapIndexed { i, b -> names[i] to b.aboveMs / MINUTE_MS }, showValues = n <= 7)
    val above = buckets.sumOf { it.aboveMs }
    val active = buckets.sumOf { it.activeMs }
    val avg = if (active > 0) (buckets.sumOf { (it.average ?: 0.0) * it.activeMs } / active).roundToInt() else 0
    ConsoleText("${w.above}: ${above / MINUTE_MS} min in total", color = if (above > 0) Amber else TextDim)
    ConsoleText("${w.active}: ${active / MINUTE_MS} min · ${w.average}: $avg%", color = TextDim, size = 12.sp)
    val withData = buckets.count { it.average != null }
    val what = w.average.lowercase()
    ConsoleText(
        if (weekly) "Points: weekly $what, weighted by ${w.activeTime} ($withData of $n weeks)."
        else "Points: $what on each ${w.activeDay} ($withData of $n).",
        color = TextDim, size = 12.sp,
    )
}

/** Week: every weekday. Month: about one date a week. 6 months: the month name where a new month starts. */
private fun bucketLabels(buckets: List<TrendBucket>, range: TrendRange): List<String> {
    val ends = buckets.map { LocalDate.ofEpochDay(it.lastDay) }
    return when (range) {
        TrendRange.WEEK -> ends.map { weekday(it) }
        TrendRange.MONTH -> ends.mapIndexed { i, d ->
            if ((i % 7 == 0 && i <= ends.lastIndex - 4) || i == ends.lastIndex) "${d.dayOfMonth} ${monthName(d)}" else ""
        }
        else -> {
            var lastShown = -3
            ends.mapIndexed { i, d ->
                if (i > 0 && d.month != ends[i - 1].month && i - lastShown >= 3) monthName(d).also { lastShown = i } else ""
            }
        }
    }
}

@Composable
private fun NotEnough(hint: String) {
    Column(Modifier.fillMaxWidth().height(120.dp), verticalArrangement = Arrangement.Center) {
        ConsoleText("> Not enough data yet_", color = Neon)
        ConsoleText(hint, color = TextDim, size = 12.sp)
    }
}

private fun weekday(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
private fun monthName(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
