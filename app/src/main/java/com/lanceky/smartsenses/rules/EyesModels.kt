package com.lanceky.smartsenses.rules

// Plain Kotlin only (no android.* imports), like the Ears rules.

/** The screen and the room this tick. lux is null when the phone has no light sensor or it hasn't reported yet. */
data class EyesSample(
    val brightnessPercent: Int,
    val autoBrightness: Boolean,
    val lux: Float?,
)

data class EyesConfig(
    val enabled: Boolean = false,
    val brightnessPercent: Int = 70,
    val dimLux: Int = 20,
    val minutes: Int = 10,
    val demoMode: Boolean = false,
) {
    /** Demo mode shortens the Eyes timer to the same 1 minute as Ears. */
    val warnMs: Long get() = (if (demoMode) EarsConfig.DEMO_CONTINUOUS_MINUTES else minutes) * MINUTE_MS

    fun isBrightInDim(sample: EyesSample): Boolean =
        sample.lux != null && sample.lux < dimLux && sample.brightnessPercent >= brightnessPercent

    companion object {
        /** A short flicker (a passing shadow, a white page lighting the sensor) doesn't restart the count. */
        const val BREAK_GRACE_MS = 5_000L
        const val RULE = "BRIGHT_IN_DIM"
    }
}

/** An Eyes card is always amber: a nudge, not a health claim. */
data class EyesWarning(
    val brightnessPercent: Int,
    val lux: Int,
    val durationMs: Long,
    val lateNight: Boolean,
    override val demoMode: Boolean,
) : CardWarning {
    val minutes: Long get() = durationMs / MINUTE_MS
    override val severity: Severity get() = Severity.CAUTION

    override val message: String
        get() = if (lateNight) "EYES: Bright screen in a dark room late at night for $minutes min. Consider lowering brightness."
        else "EYES: Bright screen in a dim room for $minutes min. Consider lowering brightness."
}

data class EyesTickResult(
    /** The timer started this tick. */
    val started: Boolean,
    /** The timer was reset this tick (brightness lowered, room lit, screen off, or Eyes switched off). */
    val reset: Boolean,
    val brightInDimMs: Long,
    val warning: EyesWarning?,
)
