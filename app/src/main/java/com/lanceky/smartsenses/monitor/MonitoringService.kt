package com.lanceky.smartsenses.monitor

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.lanceky.smartsenses.SmartSensesApp
import com.lanceky.smartsenses.data.Event
import com.lanceky.smartsenses.data.EventKind
import com.lanceky.smartsenses.data.Settings
import com.lanceky.smartsenses.overlay.WarningCardOverlay
import com.lanceky.smartsenses.rules.AudioSample
import com.lanceky.smartsenses.rules.EarsConfig
import com.lanceky.smartsenses.rules.EarsRuleEngine
import com.lanceky.smartsenses.rules.Gates
import com.lanceky.smartsenses.rules.Output
import com.lanceky.smartsenses.rules.Rule
import com.lanceky.smartsenses.rules.Warning
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** The foreground service that keeps watching headphone volume while other apps are open. */
class MonitoringService : LifecycleService() {
    private val app get() = application as SmartSensesApp
    private val engine = EarsRuleEngine()
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
        settings = app.settingsStore.settings.first()
        seedEngine()
        LiveStatus.update { it.copy(running = true, todayHighMs = engine.todayHighMs) }
        if (reason == REASON_USER) log(EventKind.MONITORING_STARTED, "monitoring started")
        else log(EventKind.MONITORING_RESUMED, "monitoring resumed ($reason)")

        lifecycleScope.launch {
            var lastCapReset = settings.capResetAt
            app.settingsStore.settings.collect { s ->
                settings = s
                // Demo mode was switched on: earlier cards must not block the next one.
                if (s.capResetAt != lastCapReset) engine.resetCooldownsAndCap()
                lastCapReset = s.capResetAt
                if (!s.monitoringEnabled) stopSelf()
            }
        }

        val reader = AudioReader(this)
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        var lastOutput: Output? = null
        while (lifecycleScope.isActive) {
            val now = System.currentTimeMillis()
            val sample = reader.read()
            val config = settings.toConfig()
            val gates = Gates(
                snoozeUntil = settings.snoozeUntil,
                screenUsable = power.isInteractive && !keyguard.isKeyguardLocked,
            )
            val result = engine.onTick(SystemClock.elapsedRealtime(), now, LocalDate.now().toEpochDay(), sample, config, gates)

            if (sample.output != lastOutput) {
                if (sample.output != null) {
                    log(EventKind.HEADPHONES_CONNECTED, "headphones connected (${sample.output.name})", outputType = sample.output.name)
                } else {
                    log(EventKind.HEADPHONES_DISCONNECTED, "headphones disconnected", outputType = lastOutput?.name)
                }
                lastOutput = sample.output
            }
            val v = sample.volumePercent
            val t = config.thresholdPercent
            if (result.stretchStarted) {
                log(EventKind.HIGH_VOLUME_STARTED, "volume $v% ≥ $t%: high-volume timer started", volumePercent = v, outputType = sample.output?.name)
            }
            if (result.stretchEnded && sample.headphones) {
                log(EventKind.VOLUME_BACK_UNDER, "volume back under threshold ($v% < $t%): timer reset", volumePercent = v, outputType = sample.output?.name)
            }
            result.warning?.let { onWarning(it, sample) }

            LiveStatus.update {
                it.copy(
                    running = true,
                    output = sample.output,
                    volumePercent = v,
                    playing = sample.playing,
                    continuousHighMs = result.continuousHighMs,
                    veryHighMs = result.veryHighMs,
                    todayHighMs = result.todayHighMs,
                    status = when {
                        !sample.headphones -> EarsStatus.NORMAL
                        v >= EarsConfig.VERY_HIGH_PERCENT -> EarsStatus.VERY_HIGH
                        v >= t -> EarsStatus.HIGH
                        else -> EarsStatus.NORMAL
                    },
                )
            }
            delay(TICK_MS)
        }
    }

    /** Restores today's loud time, cards shown and cooldowns from Room, so a restart can't re-show cards. */
    private suspend fun seedEngine() {
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val events = app.db.events()
        val lastFired = Rule.entries.mapNotNull { rule ->
            events.lastWarningTime(rule.name, settings.capResetAt)?.let { rule to it }
        }.toMap()
        engine.seed(
            epochDay = today.toEpochDay(),
            todayHighMs = app.db.readings().highMsOnDay(today.toEpochDay()),
            cardsToday = events.warningCountSince(maxOf(startOfDay, settings.capResetAt)),
            lastFired = lastFired,
            dailyFiredToday = events.lastWarningTime(Rule.DAILY.name, startOfDay) != null,
        )
    }

    private suspend fun onWarning(warning: Warning, sample: AudioSample) {
        val id = app.db.events().insert(
            Event(
                time = System.currentTimeMillis(),
                kind = EventKind.WARNING,
                message = warning.message,
                rule = warning.rule.name,
                severity = warning.severity.name,
                volumePercent = warning.volumePercent,
                durationMs = warning.durationMs,
                outputType = sample.output?.name,
                demoMode = warning.demoMode,
            )
        )
        WarningCardOverlay.show(this, warning, id)
    }

    private suspend fun log(kind: String, message: String, volumePercent: Int? = null, outputType: String? = null) {
        app.db.events().insert(
            Event(
                time = System.currentTimeMillis(),
                kind = kind,
                message = message,
                volumePercent = volumePercent,
                outputType = outputType,
                demoMode = settings.demoMode,
            )
        )
    }

    override fun onDestroy() {
        WarningCardOverlay.remove(this)
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

fun Settings.toConfig() = EarsConfig(
    thresholdPercent = thresholdPercent,
    continuousMinutes = continuousMinutes,
    dailyMinutes = dailyMinutes,
    demoMode = demoMode,
)
