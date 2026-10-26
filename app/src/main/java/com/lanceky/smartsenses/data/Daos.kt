package com.lanceky.smartsenses.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Insert
    suspend fun insert(reading: Reading): Long

    @Query("SELECT COALESCE(SUM(highMs), 0) FROM readings WHERE epochDay = :day")
    suspend fun highMsOnDay(day: Long): Long

    @Query("SELECT MAX(time) FROM readings WHERE sessionId = :sessionId")
    suspend fun lastTimeForSession(sessionId: Long): Long?
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

    @Query("SELECT COUNT(*) FROM events WHERE kind = 'WARNING' AND time >= :since")
    suspend fun warningCountSince(since: Long): Int

    @Query("SELECT MAX(time) FROM events WHERE kind = 'WARNING' AND rule = :rule AND time >= :since")
    suspend fun lastWarningTime(rule: String, since: Long): Long?
}
