package com.lanceky.smartsenses.monitor

import com.lanceky.smartsenses.rules.Output
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class EarsStatus { NORMAL, HIGH, VERY_HIGH }

/** Live state the service writes and the screens read. In memory only; re-seeded from Room on start. */
data class Live(
    val running: Boolean = false,
    val output: Output? = null,
    val volumePercent: Int = 0,
    val playing: Boolean = false,
    val continuousHighMs: Long = 0,
    val veryHighMs: Long = 0,
    val todayHighMs: Long = 0,
    val sessionListeningMs: Long = 0,
    val status: EarsStatus = EarsStatus.NORMAL,
)

object LiveStatus {
    private val _state = MutableStateFlow(Live())
    val state: StateFlow<Live> = _state.asStateFlow()

    fun update(transform: (Live) -> Live) = _state.update(transform)
}
