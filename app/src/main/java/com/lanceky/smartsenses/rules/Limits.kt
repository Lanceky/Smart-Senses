package com.lanceky.smartsenses.rules

import kotlin.math.roundToInt

// Plain Kotlin only (no android.* imports), like the other rules.

enum class LimitKind { VOLUME, BRIGHTNESS }

/** The caps you commit to in Settings. Smart-Senses turns the phone back down to them and says so. */
object Limits {
    /** At most one card per signal in this window; the cap itself is held every time. */
    const val NUDGE_GAP_MS = 15_000L

    /** The highest volume step whose % (rounded, as the dashboard shows it) stays at or under the limit. */
    fun maxVolumeIndex(limitPercent: Int, min: Int, max: Int): Int {
        if (max <= min) return max
        var step = max
        while (step > min && ((step - min) * 100f / (max - min)).roundToInt() > limitPercent) step--
        return step
    }

    /** The highest 0–255 brightness value whose % (as Eyes reads it) stays at or under the limit. */
    fun maxBrightnessRaw(limitPercent: Int): Int = limitPercent.coerceIn(0, 100) * 255 / 100

    private val volumeLines: List<(Int) -> String> = listOf(
        { "We'd talked about this… volume stays at $it%." },
        { "Nice try. You and your ears agreed on $it%." },
        { "Volume held at $it%. Past you made a promise; present you is keeping it." },
        { "Easy, DJ. The deal was $it%, and it's holding." },
        { "$it% is plenty. Your future ears say thanks." },
    )
    private val brightnessLines: List<(Int) -> String> = listOf(
        { "We'd talked about this… brightness stays at $it%." },
        { "Your eyes called. They'd like $it%, please." },
        { "Brightness held at $it%. The sun can stay outside." },
        { "Nice try. The deal was $it% brightness." },
        { "Easy on the glow. Brightness stays at $it%." },
    )

    fun line(kind: LimitKind, limitPercent: Int, n: Int): String {
        val lines = if (kind == LimitKind.VOLUME) volumeLines else brightnessLines
        return lines[Math.floorMod(n, lines.size)](limitPercent)
    }
}

/** Decides which held limits get a card: one per signal every 15 s, each with the next line. */
class LimitNudger {
    private val lastAt = mutableMapOf<LimitKind, Long>()
    private val shown = mutableMapOf<LimitKind, Int>()

    /** Returns the line number for a new card, or null while the last card for this signal is still recent. */
    fun onHeld(kind: LimitKind, elapsedRealtime: Long): Int? {
        val last = lastAt[kind]
        if (last != null && elapsedRealtime - last < Limits.NUDGE_GAP_MS) return null
        lastAt[kind] = elapsedRealtime
        val n = shown[kind] ?: 0
        shown[kind] = n + 1
        return n
    }
}

/** The card shown when a limit is held. Neon, not amber: nothing is wrong, the deal is working. */
data class LimitNudge(
    val kind: LimitKind,
    val limitPercent: Int,
    val triedPercent: Int,
    override val message: String,
    override val demoMode: Boolean,
) : CardWarning {
    override val severity: Severity get() = Severity.NORMAL
    override val heading: String get() = "● LIMIT HELD"
    override val hint: String get() = "tap to open · swipe up to dismiss"
}
