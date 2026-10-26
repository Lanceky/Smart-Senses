package com.lanceky.smartsenses.rules

import com.lanceky.smartsenses.rules.EarsConfig.Companion.COOLDOWN_MINUTES
import com.lanceky.smartsenses.rules.EarsConfig.Companion.DAILY_CAP
import com.lanceky.smartsenses.rules.EarsConfig.Companion.MAX_DELTA_MS
import com.lanceky.smartsenses.rules.EarsConfig.Companion.VERY_HIGH_MINUTES
import com.lanceky.smartsenses.rules.EarsConfig.Companion.VERY_HIGH_PERCENT

/**
 * Turns one audio sample per tick into timers and, at most, one warning.
 * Pure logic: the service feeds it time, the sample, settings and gates, and acts on the result.
 */
class EarsRuleEngine {
    var continuousHighMs = 0L
        private set
    var veryHighMs = 0L
        private set
    var todayHighMs = 0L
        private set
    var cardsToday = 0
        private set

    private var day: Long? = null
    private var lastTick: Long? = null
    private val lastFired = mutableMapOf<Rule, Long>()
    private var dailyFiredToday = false
    private var redFiredThisStretch = false
    private var inStretch = false

    /** Restores what Room remembers after a restart, so a restart can't re-show cards or forget today's loud time. */
    fun seed(epochDay: Long, todayHighMs: Long, cardsToday: Int, lastFired: Map<Rule, Long>, dailyFiredToday: Boolean) {
        day = epochDay
        this.todayHighMs = todayHighMs
        this.cardsToday = cardsToday
        this.lastFired.clear()
        this.lastFired.putAll(lastFired)
        this.dailyFiredToday = dailyFiredToday
    }

    /** Demo mode: earlier test runs never block a card during the recording. */
    fun resetCooldownsAndCap() {
        lastFired.clear()
        cardsToday = 0
    }

    fun onTick(
        elapsedRealtime: Long,
        now: Long,
        epochDay: Long,
        sample: AudioSample,
        config: EarsConfig,
        gates: Gates,
    ): TickResult {
        // A sleeping phone or a delayed tick never counts as more than 5 s of listening.
        val delta = lastTick?.let { (elapsedRealtime - it).coerceIn(0L, MAX_DELTA_MS) } ?: 0L
        lastTick = elapsedRealtime

        if (day != null && day != epochDay) {
            todayHighMs = 0
            cardsToday = 0
            dailyFiredToday = false
        }
        day = epochDay

        val listening = sample.headphones && sample.playing
        val high = sample.headphones && sample.volumePercent >= config.thresholdPercent
        var started = false
        var ended = false

        if (!high) {
            ended = inStretch
            inStretch = false
            continuousHighMs = 0
            veryHighMs = 0
            redFiredThisStretch = false
        } else {
            if (sample.volumePercent < VERY_HIGH_PERCENT) veryHighMs = 0
            if (sample.playing) {
                // Paused playback holds the timers: nothing is added and nothing resets.
                if (!inStretch) {
                    inStretch = true
                    started = true
                }
                continuousHighMs += delta
                todayHighMs += delta
                if (sample.volumePercent >= VERY_HIGH_PERCENT) veryHighMs += delta
            }
        }

        val warning = if (listening) evaluate(now, sample, config, gates) else null
        return TickResult(
            playingMs = if (listening) delta else 0,
            highMs = if (listening && high) delta else 0,
            stretchStarted = started,
            stretchEnded = ended,
            warning = warning,
            continuousHighMs = continuousHighMs,
            veryHighMs = veryHighMs,
            todayHighMs = todayHighMs,
        )
    }

    private fun evaluate(now: Long, sample: AudioSample, config: EarsConfig, gates: Gates): Warning? {
        val due = buildList {
            if (veryHighMs >= VERY_HIGH_MINUTES * MINUTE_MS) add(Rule.VERY_HIGH to veryHighMs)
            if (continuousHighMs >= config.continuousMs && !redFiredThisStretch) add(Rule.CONTINUOUS to continuousHighMs)
            if (todayHighMs >= config.dailyMs && !dailyFiredToday) add(Rule.DAILY to todayHighMs)
        }
        if (due.isEmpty()) return null
        if (cardsToday >= DAILY_CAP || now < gates.snoozeUntil || !gates.screenUsable) return null

        val (rule, duration) = due.firstOrNull { (rule, _) ->
            val last = lastFired[rule]
            last == null || now - last >= COOLDOWN_MINUTES * MINUTE_MS
        } ?: return null

        lastFired[rule] = now
        cardsToday++
        when (rule) {
            Rule.VERY_HIGH -> redFiredThisStretch = true
            Rule.DAILY -> dailyFiredToday = true
            Rule.CONTINUOUS -> Unit
        }
        val severity = if (rule == Rule.VERY_HIGH) Severity.DANGER else Severity.CAUTION
        return Warning(rule, severity, sample.volumePercent, duration, config.demoMode)
    }
}
