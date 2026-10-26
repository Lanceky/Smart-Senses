package com.lanceky.smartsenses.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EarsRuleEngineTest {
    private val day = 20_000L
    private var clock = 0L
    private var wall = 1_700_000_000_000L
    private lateinit var engine: EarsRuleEngine
    private var config = EarsConfig()
    private var gates = Gates()

    private fun loud(volume: Int = 85, output: Output? = Output.WIRED, playing: Boolean = true) =
        AudioSample(output, volume, playing)

    private fun tick(sample: AudioSample = loud(), epochDay: Long = day, stepMs: Long = 1_000): TickResult {
        clock += stepMs
        wall += stepMs
        return engine.onTick(clock, wall, epochDay, sample, config, gates)
    }

    /** One-second ticks; returns the warnings that fired. */
    private fun run(seconds: Int, sample: AudioSample = loud(), epochDay: Long = day): List<Warning> =
        (1..seconds).mapNotNull { tick(sample, epochDay).warning }

    @Before
    fun setUp() {
        engine = EarsRuleEngine()
        tick(loud(volume = 0)) // first tick only sets the clock
    }

    @Test
    fun amberFiresAt15MinutesNotBefore() {
        assertTrue(run(899).isEmpty())
        val w = run(1).single()
        assertEquals(Rule.CONTINUOUS, w.rule)
        assertEquals(Severity.CAUTION, w.severity)
        assertEquals("EARS: Volume high for 15 min. Consider lowering it.", w.message)
    }

    @Test
    fun demoModeFiresAfterOneMinute() {
        config = EarsConfig(demoMode = true)
        assertTrue(run(59).isEmpty())
        val w = run(1).single()
        assertEquals("EARS: Volume high for 1 min. Consider lowering it.", w.message)
        assertTrue(w.demoMode)
    }

    @Test
    fun pausingHoldsTheTimer() {
        run(600)
        assertTrue(run(300, loud(playing = false)).isEmpty())
        assertEquals(600_000L, engine.continuousHighMs)
        assertTrue(run(299).isEmpty())
        assertEquals(Rule.CONTINUOUS, run(1).single().rule)
    }

    @Test
    fun loweringTheVolumeResetsTheTimerButKeepsTodaysTotal() {
        run(600)
        val r = tick(loud(volume = 50))
        assertTrue(r.stretchEnded)
        assertEquals(0L, engine.continuousHighMs)
        assertEquals(600_000L, engine.todayHighMs)
        assertTrue(tick().stretchStarted)
        assertTrue(run(898).isEmpty())
        assertEquals(Rule.CONTINUOUS, run(1).single().rule)
    }

    @Test
    fun disconnectingResetsTheTimer() {
        run(600)
        assertTrue(tick(loud(output = null)).stretchEnded)
        assertEquals(0L, engine.continuousHighMs)
        assertTrue(run(899).isEmpty())
        assertEquals(1, run(1).size)
    }

    @Test
    fun speakerNeverCounts() {
        assertTrue(run(1200, loud(volume = 100, output = null)).isEmpty())
        assertEquals(0L, engine.todayHighMs)
    }

    @Test
    fun cooldownBlocksForTenMinutesThenRemindsWithRealLength() {
        assertEquals(1, run(900).size)
        assertTrue(run(599).isEmpty())
        val reminder = run(1).single()
        assertEquals(25L, reminder.minutes)
    }

    @Test
    fun capOfThreeCardsPerDayHolds() {
        val warnings = run(70 * 60)
        assertEquals(3, warnings.size)
        assertEquals(3, engine.cardsToday)
    }

    @Test
    fun snoozeBlocksUntilItEnds() {
        gates = Gates(snoozeUntil = wall + 1_200_000)
        assertTrue(run(1199).isEmpty())
        assertEquals(20L, run(1).single().minutes)
    }

    @Test
    fun lockedOrOffScreenBlocks() {
        gates = Gates(screenUsable = false)
        assertTrue(run(1000).isEmpty())
        gates = Gates(screenUsable = true)
        assertEquals(1, run(1).size)
    }

    @Test
    fun redWinsOverAmberAndAmberIsSkippedForThatStretch() {
        config = EarsConfig(continuousMinutes = 5)
        val first = run(300, loud(volume = 100)).single()
        assertEquals(Rule.VERY_HIGH, first.rule)
        assertEquals(Severity.DANGER, first.severity)
        assertEquals("EARS: Volume very high for 5 min. Lower it to protect your hearing.", first.message)
        assertTrue(run(60, loud(volume = 100)).isEmpty())
    }

    @Test
    fun droppingBelow95ResetsOnlyTheVeryHighTimer() {
        run(200, loud(volume = 100))
        tick(loud(volume = 90))
        assertEquals(0L, engine.veryHighMs)
        assertEquals(201_000L, engine.continuousHighMs)
    }

    @Test
    fun dailyCardFiresOncePerDay() {
        config = EarsConfig(continuousMinutes = 60, dailyMinutes = 15)
        assertTrue(run(600).isEmpty())
        tick(loud(volume = 50))
        val daily = run(300).single()
        assertEquals(Rule.DAILY, daily.rule)
        assertEquals("EARS: 15 min at high volume today. Give your ears a break.", daily.message)
        tick(loud(volume = 50))
        assertTrue(run(30 * 60).isEmpty())
    }

    @Test
    fun midnightResetsTodaysCounts() {
        assertEquals(3, run(70 * 60).size)
        val next = tick(epochDay = day + 1)
        assertEquals(1_000L, next.todayHighMs)
        assertEquals(Rule.CONTINUOUS, next.warning?.rule)
        assertEquals(1, engine.cardsToday)
    }

    @Test
    fun demoModeResetClearsCooldownAndCap() {
        assertEquals(3, run(70 * 60).size)
        engine.resetCooldownsAndCap()
        assertEquals(0, engine.cardsToday)
        assertEquals(1, run(1).size)
    }

    @Test
    fun deltaIsCappedAtFiveSeconds() {
        tick()
        tick(stepMs = 60_000)
        assertEquals(6_000L, engine.continuousHighMs)
    }

    @Test
    fun seedingRestoresCooldownAndCap() {
        engine.seed(day, todayHighMs = 0, cardsToday = 3, lastFired = emptyMap(), dailyFiredToday = false)
        assertTrue(run(900).isEmpty())
    }
}
