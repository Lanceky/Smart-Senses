package com.lanceky.smartsenses.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class PatternsTest {
    private fun day(d: Long, avg: Double, playingMs: Long = 30 * MINUTE_MS) =
        DayStat(epochDay = d, avgVolume = avg, highMs = 0, playingMs = playingMs, readings = 10)

    @Test fun threeListeningDaysIsNotEnough() {
        val trend = Patterns.volumeTrend(listOf(day(1, 70.0), day(2, 80.0), day(3, 90.0)))
        assertEquals(VolumeTrend.NotEnoughData(3), trend)
        assertEquals("Volume trend: Not enough data yet (3 of 4 listening days).", Patterns.trendText(trend))
    }

    @Test fun riseOfFivePointsIsTrendingUp() {
        val trend = Patterns.volumeTrend(listOf(day(1, 76.0), day(2, 80.0), day(3, 82.0), day(4, 84.0)))
        assertEquals(VolumeTrend.Up(78, 83), trend)
    }

    @Test fun riseOfFourPointsIsNotTrendingUp() {
        val trend = Patterns.volumeTrend(listOf(day(1, 76.0), day(2, 80.0), day(3, 82.0), day(4, 82.0)))
        assertEquals(VolumeTrend.NotUp(78, 82), trend)
        assertEquals("No upward volume trend (78% → 82%).", Patterns.trendText(trend))
    }

    @Test fun onlyTheLatestFourListeningDaysCount() {
        // Day 1 is old and very quiet; including it would turn a flat week into a rise.
        val trend = Patterns.volumeTrend(
            listOf(day(1, 20.0), day(2, 80.0), day(3, 80.0), day(4, 80.0), day(5, 80.0))
        )
        assertEquals(VolumeTrend.NotUp(80, 80), trend)
    }

    @Test fun daysWithoutPlayingTimeAreSkipped() {
        val trend = Patterns.volumeTrend(
            listOf(day(1, 70.0), day(2, 70.0), day(3, 0.0, playingMs = 0), day(4, 80.0), day(5, 80.0))
        )
        assertEquals(VolumeTrend.Up(70, 80), trend)
        assertEquals(VolumeTrend.NotEnoughData(2), Patterns.volumeTrend(listOf(day(1, 70.0), day(2, 0.0, 0), day(3, 80.0))))
    }

    @Test fun todaySummary() {
        assertEquals("No high-volume listening recorded today.", Patterns.todayText(0, 0, 80))
        assertEquals("Today: 1 high-volume stretch, 2 min at or above 80%.", Patterns.todayText(1, 2 * MINUTE_MS + 5_000, 80))
        assertEquals("Today: 3 high-volume stretches, 24 min at or above 85%.", Patterns.todayText(3, 24 * MINUTE_MS, 85))
    }
}
