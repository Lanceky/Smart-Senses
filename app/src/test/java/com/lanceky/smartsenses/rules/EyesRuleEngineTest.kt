package com.lanceky.smartsenses.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EyesRuleEngineTest {
    private var clock = 0L
    private var wall = 1_700_000_000_000L
    private lateinit var engine: EyesRuleEngine
    private var config = EyesConfig(enabled = true)
    private var gates = Gates()
    private var blocked = false
    private var lateNight = false

    private fun sample(brightness: Int = 85, lux: Float? = 5f) = EyesSample(brightness, autoBrightness = false, lux = lux)

    private fun tick(s: EyesSample = sample()): EyesTickResult {
        clock += 1_000
        wall += 1_000
        return engine.onTick(clock, wall, s, config, gates, blocked, lateNight)
    }

    /** One-second ticks; returns the warnings that fired. */
    private fun run(seconds: Int, s: EyesSample = sample()): List<EyesWarning> = (1..seconds).mapNotNull { tick(s).warning }

    @Before
    fun setUp() {
        engine = EyesRuleEngine()
    }

    @Test
    fun firesAfter10MinutesOfBrightScreenInDimRoom() {
        assertTrue(tick().started)
        assertTrue(run(599).isEmpty())
        val w = run(1).single()
        assertEquals(Severity.CAUTION, w.severity)
        assertEquals(85, w.brightnessPercent)
        assertEquals(5, w.lux)
        assertEquals(10, w.minutes)
        assertEquals("EYES: Bright screen in a dim room for 10 min. Consider lowering brightness.", w.message)
    }

    @Test
    fun litRoomLowBrightnessOrNoSensorNeverStartsTheTimer() {
        assertTrue(run(700, sample(lux = 300f)).isEmpty())
        assertTrue(run(700, sample(brightness = 40)).isEmpty())
        assertTrue(run(700, sample(lux = null)).isEmpty())
        assertEquals(0, engine.brightInDimMs)
    }

    @Test
    fun shortFlickerHoldsTheTimerButALongerBreakResetsIt() {
        run(120)
        run(4, sample(lux = 200f)) // under 5 s: held, not reset
        assertEquals(119_000, engine.brightInDimMs)
        run(10)
        assertEquals(129_000, engine.brightInDimMs)
        val resets = (1..5).count { tick(sample(lux = 200f)).reset }
        assertEquals(1, resets)
        assertEquals(0, engine.brightInDimMs)
    }

    @Test
    fun screenOffOrLockedResetsAtOnceAndNeverFires() {
        run(300)
        gates = Gates(screenUsable = false)
        assertTrue(tick().reset)
        assertTrue(run(700).isEmpty())
        assertEquals(0, engine.brightInDimMs)
    }

    @Test
    fun switchedOffNeverFires() {
        config = EyesConfig(enabled = false)
        assertTrue(run(700).isEmpty())
    }

    @Test
    fun demoModeFiresAfterOneMinute() {
        config = config.copy(demoMode = true)
        tick()
        assertTrue(run(59).isEmpty())
        val w = run(1).single()
        assertTrue(w.demoMode)
        assertEquals(1, w.minutes)
    }

    @Test
    fun cooldownThenAReminderIfStillBrightInDim() {
        tick()
        assertEquals(1, run(600).size)
        assertTrue(run(599).isEmpty()) // 10-minute cooldown
        val reminder = run(1).single()
        assertEquals(20, reminder.minutes)
    }

    @Test
    fun blockedOrSnoozedHoldsTheCardUntilLifted() {
        blocked = true
        tick()
        assertTrue(run(700).isEmpty())
        blocked = false
        gates = Gates(snoozeUntil = wall + 30_000)
        assertTrue(run(29).isEmpty())
        assertEquals(1, run(2).size)
    }

    @Test
    fun seededCooldownSurvivesARestart() {
        config = config.copy(demoMode = true)
        engine.seed(lastFired = wall)
        tick()
        assertTrue(run(598).isEmpty()) // due after 1 min, but the card from before the restart is still cooling down
        assertEquals(1, run(2).size)
    }

    @Test
    fun demoModeResetClearsTheCooldown() {
        config = config.copy(demoMode = true)
        engine.seed(lastFired = wall)
        engine.resetCooldown()
        tick()
        assertEquals(1, run(60).size)
    }

    @Test
    fun lateNightWording() {
        lateNight = true
        tick()
        val w = run(600).single()
        assertFalse(w.message.contains("dim room"))
        assertEquals("EYES: Bright screen in a dark room late at night for 10 min. Consider lowering brightness.", w.message)
    }

    @Test
    fun earsCountsEyesCardsTowardTheSharedDailyCap() {
        val ears = EarsRuleEngine()
        repeat(EarsConfig.DAILY_CAP) { ears.countOtherCard() }
        var t = 0L
        val fired = (0..1000).mapNotNull {
            t += 1_000
            ears.onTick(t, 1_700_000_000_000L + t, 20_000L, AudioSample(Output.WIRED, 85, true), EarsConfig(), Gates()).warning
        }
        assertTrue(fired.isEmpty())
    }
}
