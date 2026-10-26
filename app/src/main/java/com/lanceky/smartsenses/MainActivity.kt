package com.lanceky.smartsenses

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lanceky.smartsenses.data.Settings
import com.lanceky.smartsenses.monitor.LiveStatus
import com.lanceky.smartsenses.monitor.MonitoringService
import com.lanceky.smartsenses.ui.AppScaffold
import com.lanceky.smartsenses.ui.onboarding.OnboardingFlow
import com.lanceky.smartsenses.ui.theme.Black
import com.lanceky.smartsenses.ui.theme.SmartSensesTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as SmartSensesApp

    companion object {
        /** Set when a warning card is tapped: the app opens that warning's Insight. */
        const val EXTRA_WARNING_ID = "warningId"
    }

    private var openWarningId by mutableStateOf<Long?>(null)

    private fun readWarningId(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_WARNING_ID, -1L) ?: -1L
        if (id > 0) openWarningId = id
        intent?.removeExtra(EXTRA_WARNING_ID)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readWarningId(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readWarningId(intent)
        setContent {
            SmartSensesTheme {
                // Wait on a black screen until settings load, so nothing flashes.
                val settings by app.settingsStore.settings.collectAsStateWithLifecycle<Settings?>(initialValue = null)
                Box(Modifier.fillMaxSize().background(Black)) {
                    settings?.let {
                        if (it.onboardingDone) {
                            AppScaffold(it, openWarningId, onOpenWarning = { id -> openWarningId = id }, onCloseInsight = { openWarningId = null })
                        } else OnboardingFlow()
                    }
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
