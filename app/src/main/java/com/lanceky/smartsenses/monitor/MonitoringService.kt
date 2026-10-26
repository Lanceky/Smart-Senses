package com.lanceky.smartsenses.monitor

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.lanceky.smartsenses.SmartSensesApp
import com.lanceky.smartsenses.data.Event
import com.lanceky.smartsenses.data.EventKind
import com.lanceky.smartsenses.data.Settings
import com.lanceky.smartsenses.rules.Output
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The foreground service that keeps watching headphone volume while other apps are open. */
class MonitoringService : LifecycleService() {
    private val app get() = application as SmartSensesApp
    private var loop: Job? = null
    private var settings = Settings()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        MonitoringNotification.ensureChannel(this)
        try {
            ServiceCompat.startForeground(
                this,
                MonitoringNotification.NOTIFICATION_ID,
                MonitoringNotification.build(this),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        } catch (e: Exception) {
            // Android refused (for example a background start). The dashboard will show STOPPED.
            Log.w(TAG, "startForeground refused", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop == null) {
            val reason = intent?.getStringExtra(EXTRA_REASON) ?: REASON_SYSTEM
            loop = lifecycleScope.launch { monitor(reason) }
        }
        return START_STICKY
    }

    private suspend fun monitor(reason: String) {
        LiveStatus.update { it.copy(running = true) }
        if (reason == REASON_USER) log(EventKind.MONITORING_STARTED, "monitoring started")
        else log(EventKind.MONITORING_RESUMED, "monitoring resumed ($reason)")

        lifecycleScope.launch {
            app.settingsStore.settings.collect { s ->
                settings = s
                if (!s.monitoringEnabled) stopSelf()
            }
        }

        val reader = AudioReader(this)
        var lastOutput: Output? = null
        while (lifecycleScope.isActive) {
            val sample = reader.read()
            if (sample.output != lastOutput) {
                when {
                    sample.output != null -> log(EventKind.HEADPHONES_CONNECTED, "headphones connected (${sample.output.name})", outputType = sample.output.name)
                    else -> log(EventKind.HEADPHONES_DISCONNECTED, "headphones disconnected", outputType = lastOutput?.name)
                }
                lastOutput = sample.output
            }
            LiveStatus.update {
                it.copy(running = true, output = sample.output, volumePercent = sample.volumePercent, playing = sample.playing)
            }
            delay(TICK_MS)
        }
    }

    private suspend fun log(kind: String, message: String, outputType: String? = null) {
        app.db.events().insert(
            Event(time = System.currentTimeMillis(), kind = kind, message = message, outputType = outputType, demoMode = settings.demoMode)
        )
    }

    override fun onDestroy() {
        LiveStatus.update { Live(running = false) }
        val demo = settings.demoMode
        app.appScope.launch {
            app.db.events().insert(
                Event(time = System.currentTimeMillis(), kind = EventKind.MONITORING_STOPPED, message = "monitoring stopped", demoMode = demo)
            )
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MonitoringService"
        const val TICK_MS = 1_000L
        const val EXTRA_REASON = "reason"
        const val REASON_USER = "user"
        const val REASON_APP_OPEN = "app open"
        const val REASON_BOOT = "boot"
        const val REASON_SYSTEM = "system restart"

        /** Returns false when Android refuses the start; the dashboard then shows MONITORING: STOPPED. */
        fun start(context: Context, reason: String): Boolean = try {
            ContextCompat.startForegroundService(
                context, Intent(context, MonitoringService::class.java).putExtra(EXTRA_REASON, reason)
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "start refused", e)
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringService::class.java))
        }
    }
}
