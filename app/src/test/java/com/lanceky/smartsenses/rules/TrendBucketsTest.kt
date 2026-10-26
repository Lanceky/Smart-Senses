package com.lanceky.smartsenses.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendBucketsTest {
    private val today = 20752L
    private fun day(d: Long, avg: Double, highMin: Long, activeMin: Long, readings: Int = 6) =
        DayStat(d, avg, highMin * MINUTE_MS, activeMin * MINUTE_MS, readings)

    @Test
    fun `daily buckets end today, oldest first, with gaps left empty`() {
        val b = TrendBuckets.of(listOf(day(today, 90.0, 5, 30), day(today - 2, 60.0, 0, 10)), today, count = 7)
        assertEquals(7, b.size)
        assertEquals(today - 6, b.first().firstDay)
        assertEquals(today, b.last().lastDay)
        assertEquals(90.0, b[6].average!!, 0.001)
        assertNull(b[5].average)
        assertEquals(60.0, b[4].average!!, 0.001)
        assertEquals(5 * MINUTE_MS, b[6].aboveMs)
    }

    @Test
    fun `weekly bucket averages by active time and sums the minutes`() {
        // 30 min at 90% and 10 min at 50%: (90×30 + 50×10) / 40 = 80.
        val b = TrendBuckets.of(listOf(day(today, 90.0, 20, 30), day(today - 3, 50.0, 0, 10)), today, count = 26, daysPerBucket = 7)
        assertEquals(26, b.size)
        val week = b.last()
        assertEquals(today - 6, week.firstDay)
        assertEquals(80.0, week.average!!, 0.001)
        assertEquals(20 * MINUTE_MS, week.aboveMs)
        assertEquals(40 * MINUTE_MS, week.activeMs)
        assertEquals(12, week.readings)
        assertEquals(today - 26 * 7 + 1, b.first().firstDay)
    }

    @Test
    fun `days outside the window and days with no active time are ignored`() {
        val b = TrendBuckets.of(listOf(day(today - 7, 95.0, 9, 9), day(today - 1, 70.0, 0, 0)), today, count = 7)
        assertEquals(0, b.sumOf { it.readings })
        b.forEach { assertNull(it.average) }
    }
}
