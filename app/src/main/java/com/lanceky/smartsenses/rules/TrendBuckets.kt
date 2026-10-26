package com.lanceky.smartsenses.rules

/**
 * One point on a Trends chart: a day, or a block of days. average is null when nothing was recorded.
 * activeMs is play time for volume or screen-on time for brightness; aboveMs is the time over the line.
 */
data class TrendBucket(
    val firstDay: Long,
    val lastDay: Long,
    val average: Double?,
    val aboveMs: Long,
    val activeMs: Long,
    val readings: Int,
)

object TrendBuckets {
    /**
     * [count] consecutive blocks of [daysPerBucket] days, oldest first, the last one ending on [today].
     * Averages are weighted by active time, so a long day counts more than a short one; minutes are summed.
     */
    fun of(stats: List<DayStat>, today: Long, count: Int, daysPerBucket: Int = 1): List<TrendBucket> {
        val byDay = stats.filter { it.playingMs > 0 }.associateBy { it.epochDay }
        return (count - 1 downTo 0).map { k ->
            val last = today - k.toLong() * daysPerBucket
            val first = last - daysPerBucket + 1
            val days = (first..last).mapNotNull { byDay[it] }
            val active = days.sumOf { it.playingMs }
            TrendBucket(
                firstDay = first,
                lastDay = last,
                average = if (active > 0) days.sumOf { it.avgVolume * it.playingMs } / active else null,
                aboveMs = days.sumOf { it.highMs },
                activeMs = active,
                readings = days.sumOf { it.readings },
            )
        }
    }
}
