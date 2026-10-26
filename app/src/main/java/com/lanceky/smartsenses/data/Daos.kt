package com.lanceky.smartsenses.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lanceky.smartsenses.rules.DayStat
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Insert
    suspend fun insert(reading: Reading): Long

    @Query("SELECT COALESCE(SUM(highMs), 0) FROM readings WHERE epochDay = :day")
    suspend fun highMsOnDay(day: Long): Long

    @Query("SELECT MAX(time) FROM readings WHERE sessionId = :sessionId")
    suspend fun lastTimeForSession(sessionId: Long): Long?

    @Query("SELECT * FROM readings WHERE epochDay = :day ORDER BY time")
    fun observeDay(day: Long): Flow<List<Reading>>

    /** Per day: play-time-weighted average volume, time at or above the threshold, play time. */
    @Query(
        """SELECT epochDay, SUM(volumePercent * playingMs) * 1.0 / SUM(playingMs) AS avgVolume,
           SUM(highMs) AS highMs, SUM(playingMs) AS playingMs, COUNT(*) AS readings
           FROM readings WHERE epochDay >= :fromDay AND playingMs > 0 GROUP BY epochDay ORDER BY epochDay"""
    )
    fun observeDailyStats(fromDay: Long): Flow<List<DayStat>>
}

@Dao
interface BrightnessDao {
    @Insert
    suspend fun insert(reading: BrightnessReading): Long

    @Query("SELECT * FROM brightness_readings WHERE epochDay = :day ORDER BY time")
    fun observeDay(day: Long): Flow<List<BrightnessReading>>

    /**
     * Per day, in the same DayStat shape as volume: avgVolume holds the screen-on-weighted average brightness,
     * highMs the time bright in a dim room, playingMs the screen-on time.
     */
    @Query(
        """SELECT epochDay, SUM(brightnessPercent * screenOnMs) * 1.0 / SUM(screenOnMs) AS avgVolume,
           SUM(brightInDimMs) AS highMs, SUM(screenOnMs) AS playingMs, COUNT(*) AS readings
           FROM brightness_readings WHERE epochDay >= :fromDay AND screenOnMs > 0 GROUP BY epochDay ORDER BY epochDay"""
    )
    fun observeDailyStats(fromDay: Long): Flow<List<DayStat>>
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: ListeningSession): Long

    @Update
    suspend fun update(session: ListeningSession)

    @Delete
    suspend fun delete(session: ListeningSession)

    @Query("SELECT * FROM listening_sessions WHERE endedAt IS NULL")
    suspend fun openSessions(): List<ListeningSession>

    @Query("SELECT * FROM listening_sessions ORDER BY startedAt DESC, id DESC")
    fun observeAll(): Flow<List<ListeningSession>>
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: Event): Long

    @Query("SELECT * FROM events ORDER BY time DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE kind = 'WARNING' ORDER BY time DESC, id DESC")
    fun observeWarnings(): Flow<List<Event>>

    @Query("SELECT COUNT(*) FROM events WHERE kind = :kind AND time >= :since")
    fun countKindSince(kind: String, since: Long): Flow<Int>

    @Query("SELECT * FROM events WHERE id = :id")
    fun observe(id: Long): Flow<Event?>

    @Query("SELECT COUNT(*) FROM events WHERE kind = 'WARNING' AND time >= :since")
    suspend fun warningCountSince(since: Long): Int

    @Query("SELECT MAX(time) FROM events WHERE kind = 'WARNING' AND rule = :rule AND time >= :since")
    suspend fun lastWarningTime(rule: String, since: Long): Long?
}
