package com.lanceky.smartsenses.rules

import com.lanceky.smartsenses.rules.EarsConfig.Companion.COOLDOWN_MINUTES
import com.lanceky.smartsenses.rules.EarsConfig.Companion.MAX_DELTA_MS
import com.lanceky.smartsenses.rules.EyesConfig.Companion.BREAK_GRACE_MS

/**
 * Times a bright screen in a dim room while the screen is on and unlocked, and fires at most one amber card.
 * The daily cap is shared with Ears, so the service tells it when cards are held back (`blocked`).
 */
class EyesRuleEngine {
    var brightInDimMs = 0L
        private set

    /** A bright-in-dim stretch is being timed (including its short grace). */
    var timing = false
        private set

    private var lastTick: Long? = null
    private var breakMs = 0L
    private var lastFired: Long? = null

    /** Restores the last Eyes card time from Room, so a restart can't re-show a card inside its cooldown. */
    fun seed(lastFired: Long?) {
        this.lastFired = lastFired
    }

    /** Demo mode: earlier test runs never block a card during the recording. */
    fun resetCooldown() {
        lastFired = null
    }

    fun onTick(
        elapsedRealtime: Long,
        now: Long,
        sample: EyesSample,
        config: EyesConfig,
        gates: Gates,
        blocked: Boolean,
        lateNight: Boolean,
    ): EyesTickResult {
        val delta = lastTick?.let { (elapsedRealtime - it).coerceIn(0L, MAX_DELTA_MS) } ?: 0L
        lastTick = elapsedRealtime

        val active = config.enabled && gates.screenUsable
        var started = false
        var reset = false
        if (active && config.isBrightInDim(sample)) {
            breakMs = 0
            if (!timing) {
                timing = true
                started = true
            } else {
                brightInDimMs += delta
            }
        } else if (timing) {
            // Screen off or Eyes off ends the stretch at once; a brighter room or lower brightness gets a short grace.
            breakMs += delta
            if (!active || breakMs >= BREAK_GRACE_MS) {
                timing = false
                brightInDimMs = 0
                breakMs = 0
                reset = true
            }
        }

        val due = timing && breakMs == 0L && brightInDimMs >= config.warnMs
        val coolingDown = lastFired?.let { now - it < COOLDOWN_MINUTES * MINUTE_MS } ?: false
        val warning = if (due && !blocked && !coolingDown && now >= gates.snoozeUntil) {
            lastFired = now
            EyesWarning(sample.brightnessPercent, sample.lux?.toInt() ?: 0, brightInDimMs, lateNight, config.demoMode)
        } else null

        return EyesTickResult(started, reset, brightInDimMs, warning)
    }
}
