package com.lanceky.smartsenses

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lanceky.smartsenses.data.Settings
import com.lanceky.smartsenses.monitor.LiveStatus
import com.lanceky.smartsenses.monitor.MonitoringService
import com.lanceky.smartsenses.ui.AppScaffold
import com.lanceky.smartsenses.ui.theme.Black
import com.lanceky.smartsenses.ui.theme.SmartSensesTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as SmartSensesApp

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            SmartSensesTheme {
                // Wait on a black screen until settings load, so nothing flashes.
                val settings by app.settingsStore.settings.collectAsStateWithLifecycle<Settings?>(initialValue = null)
                Box(Modifier.fillMaxSize().background(Black)) {
                    settings?.let { AppScaffold(it) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Self-restart: if Android or the battery manager killed the monitor, opening the app brings it back.
        lifecycleScope.launch {
            val s = app.settingsStore.settings.first()
            if (s.monitoringEnabled && !LiveStatus.state.value.running) {
                MonitoringService.start(this@MainActivity, MonitoringService.REASON_APP_OPEN)
            }
        }
    }
}
