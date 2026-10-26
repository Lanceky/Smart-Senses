package com.lanceky.smartsenses.rules

// Plain Kotlin only (no android.* imports) so the rules can move to a Kotlin Multiplatform module later.

const val MINUTE_MS = 60_000L

/** Headphone outputs that count. The built-in speaker is never an Output. */
enum class Output { WIRED, USB, BLUETOOTH }

data class AudioSample(
    val output: Output?,
    val volumePercent: Int,
    val playing: Boolean,
) {
    val headphones: Boolean get() = output != null
}

enum class Severity { NORMAL, CAUTION, DANGER }

/** Checked in this priority order. */
enum class Rule { VERY_HIGH, CONTINUOUS, DAILY }

data class EarsConfig(
    val thresholdPercent: Int = 80,
    val continuousMinutes: Int = 15,
    val dailyMinutes: Int = 60,
    val demoMode: Boolean = false,
) {
    /** Demo mode shortens only the continuous timer. */
    val continuousMs: Long get() = (if (demoMode) DEMO_CONTINUOUS_MINUTES else continuousMinutes) * MINUTE_MS
    val dailyMs: Long get() = dailyMinutes * MINUTE_MS

    companion object {
        const val VERY_HIGH_PERCENT = 95
        const val VERY_HIGH_MINUTES = 5
        const val DEMO_CONTINUOUS_MINUTES = 1
        const val COOLDOWN_MINUTES = 10
        const val DAILY_CAP = 3
        const val SNOOZE_MINUTES = 60
        const val MAX_DELTA_MS = 5_000L
    }
}

/** Things that can hold a due warning back. Suppressed warnings are neither shown nor logged. */
data class Gates(
    val snoozeUntil: Long = 0,
    val screenUsable: Boolean = true,
)

/** What the floating card needs from any signal's warning. */
interface CardWarning {
    val severity: Severity
    val message: String
    val demoMode: Boolean
}

data class Warning(
    val rule: Rule,
    override val severity: Severity,
    val volumePercent: Int,
    val durationMs: Long,
    override val demoMode: Boolean,
) : CardWarning {
    val minutes: Long get() = durationMs / MINUTE_MS

    override val message: String
        get() = when (rule) {
            Rule.VERY_HIGH -> "EARS: Volume very high for $minutes min. Lower it to protect your hearing."
            Rule.CONTINUOUS -> "EARS: Volume high for $minutes min. Consider lowering it."
            Rule.DAILY -> "EARS: $minutes min at high volume today. Give your ears a break."
        }
}

data class TickResult(
    /** Headphone playback counted this tick (for readings). */
    val playingMs: Long,
    /** Of that, time at or above the threshold. */
    val highMs: Long,
    /** First counted tick of a loud stretch. */
    val stretchStarted: Boolean,
    /** The loud stretch ended: volume dropped below the threshold or headphones disconnected. */
    val stretchEnded: Boolean,
    val warning: Warning?,
    val continuousHighMs: Long,
    val veryHighMs: Long,
    val todayHighMs: Long,
)
