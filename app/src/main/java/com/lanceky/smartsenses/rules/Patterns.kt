package com.lanceky.smartsenses.rules

import kotlin.math.roundToInt

/** One day of recorded listening. Room fills it from `readings`; avgVolume is weighted by play time. */
data class DayStat(
    val epochDay: Long,
    val avgVolume: Double,
    val highMs: Long,
    val playingMs: Long,
    val readings: Int,
)

sealed interface VolumeTrend {
    data class NotEnoughData(val listeningDays: Int) : VolumeTrend
    data class Up(val from: Int, val to: Int) : VolumeTrend
    data class NotUp(val from: Int, val to: Int) : VolumeTrend
}

/** Simple rule-based comparisons over recorded readings. No medical wording. */
object Patterns {
    const val TREND_DAYS = 4
    const val RISE_POINTS = 5

    fun todayText(stretches: Int, highMs: Long, thresholdPercent: Int): String {
        val minutes = highMs / MINUTE_MS
        if (stretches == 0 && minutes == 0L) return "No high-volume listening recorded today."
        val s = if (stretches == 1) "stretch" else "stretches"
        return "Today: $stretches high-volume $s, $minutes min at or above $thresholdPercent%."
    }

    /** The 2 most recent listening days against the 2 before them; a rise of 5 points or more is "trending up". */
    fun volumeTrend(days: List<DayStat>): VolumeTrend {
        val listening = days.filter { it.playingMs > 0 }.sortedByDescending { it.epochDay }.take(TREND_DAYS)
        if (listening.size < TREND_DAYS) return VolumeTrend.NotEnoughData(listening.size)
        val recent = (listening[0].avgVolume + listening[1].avgVolume) / 2
        val earlier = (listening[2].avgVolume + listening[3].avgVolume) / 2
        val from = earlier.roundToInt()
        val to = recent.roundToInt()
        return if (recent - earlier >= RISE_POINTS) VolumeTrend.Up(from, to) else VolumeTrend.NotUp(from, to)
    }

    fun trendText(trend: VolumeTrend): String = when (trend) {
        is VolumeTrend.NotEnoughData -> "Volume trend: Not enough data yet (${trend.listeningDays} of $TREND_DAYS listening days)."
        is VolumeTrend.Up -> "Volume trending up: ${trend.from}% → ${trend.to}% over your last $TREND_DAYS listening days."
        is VolumeTrend.NotUp -> "No upward volume trend (${trend.from}% → ${trend.to}%)."
    }
}
