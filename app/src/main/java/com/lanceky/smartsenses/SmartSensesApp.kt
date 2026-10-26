package com.lanceky.smartsenses

import android.app.Application
import com.lanceky.smartsenses.data.AppDatabase
import com.lanceky.smartsenses.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The one app container: every component gets the database and settings from here. */
class SmartSensesApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: AppDatabase by lazy { AppDatabase.build(this) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
}
