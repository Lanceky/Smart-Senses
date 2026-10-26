package com.lanceky.smartsenses.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per 10 seconds of listening through headphones (only written when audio played). */
@Entity(tableName = "readings", indices = [Index("epochDay"), Index("sessionId")])
data class Reading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val epochDay: Long,
    val volumePercent: Int,
    val outputType: String,
    val playingMs: Long,
    val highMs: Long,
    val sessionId: Long,
)

/** From headphones connected to disconnected. endedAt == null while ongoing. */
@Entity(tableName = "listening_sessions", indices = [Index("epochDay")])
data class ListeningSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val epochDay: Long,
    val outputType: String,
    val listeningMs: Long = 0,
    val highMs: Long = 0,
    val peakVolumePercent: Int = 0,
    val maxSeverity: String = "NORMAL",
)

/** Warnings and System Log lines. Only ever inserted. */
@Entity(tableName = "events", indices = [Index("time"), Index("kind")])
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val kind: String,
    val message: String,
    val rule: String? = null,
    val severity: String? = null,
    val volumePercent: Int? = null,
    val durationMs: Long? = null,
    val outputType: String? = null,
    val demoMode: Boolean = false,
)

object EventKind {
    const val MONITORING_STARTED = "MONITORING_STARTED"
    const val MONITORING_RESUMED = "MONITORING_RESUMED"
    const val MONITORING_STOPPED = "MONITORING_STOPPED"
    const val HEADPHONES_CONNECTED = "HEADPHONES_CONNECTED"
    const val HEADPHONES_DISCONNECTED = "HEADPHONES_DISCONNECTED"
    const val HIGH_VOLUME_STARTED = "HIGH_VOLUME_STARTED"
    const val VOLUME_BACK_UNDER = "VOLUME_BACK_UNDER"
    const val WARNING = "WARNING"
    const val DEMO_MODE_ON = "DEMO_MODE_ON"
    const val SNOOZED = "SNOOZED"
}
