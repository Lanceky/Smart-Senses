package com.lanceky.smartsenses.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class Settings(
    val onboardingDone: Boolean = false,
    val monitoringEnabled: Boolean = false,
    val thresholdPercent: Int = 80,
    val continuousMinutes: Int = 15,
    val dailyMinutes: Int = 60,
    val demoMode: Boolean = false,
    val capResetAt: Long = 0,
    val snoozeUntil: Long = 0,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboardingDone")
        val monitoringEnabled = booleanPreferencesKey("monitoringEnabled")
        val thresholdPercent = intPreferencesKey("thresholdPercent")
        val continuousMinutes = intPreferencesKey("continuousMinutes")
        val dailyMinutes = intPreferencesKey("dailyMinutes")
        val demoMode = booleanPreferencesKey("demoMode")
        val capResetAt = longPreferencesKey("capResetAt")
        val snoozeUntil = longPreferencesKey("snoozeUntil")
    }

    val settings: Flow<Settings> = context.settingsDataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = Settings(
        onboardingDone = this[Keys.onboardingDone] ?: false,
        monitoringEnabled = this[Keys.monitoringEnabled] ?: false,
        thresholdPercent = this[Keys.thresholdPercent] ?: 80,
        continuousMinutes = this[Keys.continuousMinutes] ?: 15,
        dailyMinutes = this[Keys.dailyMinutes] ?: 60,
        demoMode = this[Keys.demoMode] ?: false,
        capResetAt = this[Keys.capResetAt] ?: 0L,
        snoozeUntil = this[Keys.snoozeUntil] ?: 0L,
    )

    suspend fun setOnboardingDone(done: Boolean) = edit { it[Keys.onboardingDone] = done }
    suspend fun setMonitoringEnabled(enabled: Boolean) = edit { it[Keys.monitoringEnabled] = enabled }
    suspend fun setThresholdPercent(value: Int) = edit { it[Keys.thresholdPercent] = value.coerceIn(50, 95) }
    suspend fun setContinuousMinutes(value: Int) = edit { it[Keys.continuousMinutes] = value.coerceIn(5, 60) }
    suspend fun setDailyMinutes(value: Int) = edit { it[Keys.dailyMinutes] = value.coerceIn(15, 240) }
    suspend fun setSnoozeUntil(time: Long) = edit { it[Keys.snoozeUntil] = time }

    /** Turning Demo mode on also resets cooldowns and today's card count (capResetAt) and clears snooze. */
    suspend fun setDemoMode(on: Boolean, now: Long = System.currentTimeMillis()) = edit {
        it[Keys.demoMode] = on
        if (on) {
            it[Keys.capResetAt] = now
            it[Keys.snoozeUntil] = 0L
        }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }
}
