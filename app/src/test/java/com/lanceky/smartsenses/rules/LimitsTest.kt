package com.lanceky.smartsenses.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class LimitsTest {
    @Test
    fun `volume cap is the highest step that still reads at or under the limit`() {
        assertEquals(9, Limits.maxVolumeIndex(60, 0, 15)) // 9 of 15 = 60%
        assertEquals(9, Limits.maxVolumeIndex(65, 0, 15)) // 10 of 15 would read 67%
        assertEquals(15, Limits.maxVolumeIndex(100, 0, 15))
        for (limit in 0..100) {
            val step = Limits.maxVolumeIndex(limit, 0, 15)
            assertTrue(limit.toString(), (step * 100f / 15).roundToInt() <= limit)
            if (step < 15) assertTrue(limit.toString(), ((step + 1) * 100f / 15).roundToInt() > limit)
        }
    }

    @Test
    fun `brightness cap never reads above the limit`() {
        assertEquals(153, Limits.maxBrightnessRaw(60))
        for (limit in 0..100) {
            val raw = Limits.maxBrightnessRaw(limit)
            assertTrue(limit.toString(), (raw * 100 + 127) / 255 <= limit)
        }
    }

    @Test
    fun `one card per signal every 15 seconds, each with the next line`() {
        val nudger = LimitNudger()
        assertEquals(0, nudger.onHeld(LimitKind.VOLUME, 1_000))
        assertNull(nudger.onHeld(LimitKind.VOLUME, 10_000))
        assertEquals(0, nudger.onHeld(LimitKind.BRIGHTNESS, 10_000))
        assertEquals(1, nudger.onHeld(LimitKind.VOLUME, 16_000))
    }

    @Test
    fun `lines name the limit and rotate`() {
        val first = Limits.line(LimitKind.VOLUME, 60, 0)
        assertEquals("We'd talked about this… volume stays at 60%.", first)
        assertNotEquals(first, Limits.line(LimitKind.VOLUME, 60, 1))
        assertEquals(first, Limits.line(LimitKind.VOLUME, 60, 5))
        assertTrue(Limits.line(LimitKind.BRIGHTNESS, 40, 2).contains("40%"))
    }
}
