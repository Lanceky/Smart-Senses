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
import com.lanceky.smartsenses.data.ListeningSession
import com.lanceky.smartsenses.data.Reading
import com.lanceky.smartsenses.data.Settings
import com.lanceky.smartsenses.data.Signal
import com.lanceky.smartsenses.overlay.WarningCardOverlay
import com.lanceky.smartsenses.rules.AudioSample
import com.lanceky.smartsenses.rules.EarsConfig
import com.lanceky.smartsenses.rules.EarsRuleEngine
import com.lanceky.smartsenses.rules.EyesConfig
import com.lanceky.smartsenses.rules.EyesRuleEngine
import com.lanceky.smartsenses.rules.EyesWarning
import com.lanceky.smartsenses.rules.Gates
import com.lanceky.smartsenses.rules.Output
import com.lanceky.smartsenses.rules.Rule
import com.lanceky.smartsenses.rules.Severity
import com.lanceky.smartsenses.rules.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The foreground service that keeps watching headphone volume while other apps are open. */
class MonitoringService : LifecycleService() {
    private val app get() = application as SmartSensesApp
    private val engine = EarsRuleEngine()
    private val eyesEngine = EyesRuleEngine()
    private var eyesReader: EyesReader? = null
    private var loop: Job? = null
    private var settings = Settings()

    // The open listening session and the reading being collected. Touched only on the main thread.
    private var session: ListeningSession? = null
    private var pendPlayingMs = 0L
    private var pendHighMs = 0L
    private var pendVolumeMs = 0L // volume % × ms, for a play-time-weighted average
    private var pendPeak = 0
    private var lastFlushAt = 0L

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
        closeStaleSessions()
        seedEngine()
        LiveStatus.update { it.copy(running = true, todayHighMs = engine.todayHighMs) }
        if (reason == REASON_USER) log(EventKind.MONITORING_STARTED, "monitoring started")
        else log(EventKind.MONITORING_RESUMED, "monitoring resumed ($reason)")

        lifecycleScope.launch {
            var lastCapReset = settings.capResetAt
            app.settingsStore.settings.collect { s ->
                settings = s
                // Demo mode was switched on: earlier cards must not block the next one.
                if (s.capResetAt != lastCapReset) {
                    engine.resetCooldownsAndCap()
                    eyesEngine.resetCooldown()
                }
                lastCapReset = s.capResetAt
                if (!s.monitoringEnabled) stopSelf()
            }
        }

        val reader = AudioReader(this)
        val eyesReader = EyesReader(this).also { this.eyesReader = it }
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        var lastOutput: Output? = null
        lastFlushAt = SystemClock.elapsedRealtime()
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
                if (lastOutput != null) {
                    flushReading(now)
                    closeSession(now)
                }
                if (sample.output != null) {
                    log(EventKind.HEADPHONES_CONNECTED, "headphones connected (${sample.output.name})", outputType = sample.output.name)
                    openSession(now, sample.output)
                } else {
                    log(EventKind.HEADPHONES_DISCONNECTED, "headphones disconnected", outputType = lastOutput?.name)
                }
                lastOutput = sample.output
            }
            if (result.playingMs > 0) {
                pendPlayingMs += result.playingMs
                pendHighMs += result.highMs
                pendVolumeMs += sample.volumePercent.toLong() * result.playingMs
                pendPeak = maxOf(pendPeak, sample.volumePercent)
            }
            if (SystemClock.elapsedRealtime() - lastFlushAt >= READING_MS) flushReading(now)
            val v = sample.volumePercent
            val t = config.thresholdPercent
            if (result.stretchStarted) {
                log(EventKind.HIGH_VOLUME_STARTED, "volume $v% ≥ $t%: high-volume timer started", volumePercent = v, outputType = sample.output?.name)
            }
            if (result.stretchEnded && sample.headphones) {
                log(EventKind.VOLUME_BACK_UNDER, "volume back under threshold ($v% < $t%): timer reset", volumePercent = v, outputType = sample.output?.name)
            }
            result.warning?.let { onWarning(it, sample) }

            // Eyes: same tick, same snooze and screen gates; the 3-a-day cap is shared, and it never replaces a card on screen.
            val eyesConfig = settings.toEyesConfig()
            eyesReader.setListening(eyesConfig.enabled)
            val eyesSample = eyesReader.read()
            val blocked = engine.cardsToday >= EarsConfig.DAILY_CAP || result.warning != null || WarningCardOverlay.isShowing
            val hour = LocalTime.now().hour
            val eyes = eyesEngine.onTick(
                SystemClock.elapsedRealtime(), now, eyesSample, eyesConfig, gates, blocked, lateNight = hour >= 22 || hour < 6,
            )
            val b = eyesSample.brightnessPercent
            val lux = eyesSample.lux?.toInt()
            if (eyes.started) {
                log(EventKind.EYES_TIMER_STARTED, "screen $b% in dim light ($lux lux): eyes timer started", signal = Signal.EYES)
            }
            if (eyes.reset) {
                val why = when {
                    !eyesConfig.enabled -> "eyes switched off"
                    !gates.screenUsable -> "screen off"
                    b < eyesConfig.brightnessPercent -> "brightness lowered to $b%"
                    else -> "room brighter ($lux lux)"
                }
                log(EventKind.EYES_TIMER_RESET, "eyes timer reset: $why", signal = Signal.EYES)
            }
            eyes.warning?.let {
                engine.countOtherCard()
                onEyesWarning(it)
            }

            LiveStatus.update {
                it.copy(
                    running = true,
                    output = sample.output,
                    volumePercent = v,
                    playing = sample.playing,
                    continuousHighMs = result.continuousHighMs,
                    veryHighMs = result.veryHighMs,
                    todayHighMs = result.todayHighMs,
                    sessionListeningMs = (session?.listeningMs ?: 0) + pendPlayingMs,
                    brightnessPercent = b,
                    autoBrightness = eyesSample.autoBrightness,
                    lightSensor = eyesReader.hasLightSensor,
                    lux = eyesSample.lux,
                    brightInDimMs = eyes.brightInDimMs,
                    eyesTiming = eyesEngine.timing,
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
        eyesEngine.seed(events.lastWarningTime(EyesConfig.RULE, settings.capResetAt))
    }

    /** Saves the last 10 s of listening as a reading and adds it to the session. Paused time never makes a row. */
    private suspend fun flushReading(now: Long) {
        lastFlushAt = SystemClock.elapsedRealtime()
        val playing = pendPlayingMs
        val high = pendHighMs
        val volume = if (playing > 0) ((pendVolumeMs + playing / 2) / playing).toInt() else 0
        val peak = pendPeak
        pendPlayingMs = 0; pendHighMs = 0; pendVolumeMs = 0; pendPeak = 0
        val s = session ?: return
        if (playing <= 0) return
        val updated = s.copy(
            listeningMs = s.listeningMs + playing,
            highMs = s.highMs + high,
            peakVolumePercent = maxOf(s.peakVolumePercent, peak),
        )
        session = updated
        app.db.readings().insert(
            Reading(
                time = now,
                epochDay = LocalDate.now().toEpochDay(),
                volumePercent = volume,
                outputType = s.outputType,
                playingMs = playing,
                highMs = high,
                sessionId = s.id,
            )
        )
        app.db.sessions().update(updated)
    }

    private suspend fun openSession(now: Long, output: Output) {
        val s = ListeningSession(startedAt = now, epochDay = LocalDate.now().toEpochDay(), outputType = output.name)
        session = s.copy(id = app.db.sessions().insert(s))
    }

    /** Closes the open session; one with no listening at all (only paused) is deleted. */
    private suspend fun closeSession(now: Long) {
        val s = session ?: return
        session = null
        if (s.listeningMs == 0L) app.db.sessions().delete(s) else app.db.sessions().update(s.copy(endedAt = now))
    }

    /** A killed process leaves its session open: close it at its last reading. */
    private suspend fun closeStaleSessions() {
        val sessions = app.db.sessions()
        for (s in sessions.openSessions()) {
            if (s.listeningMs == 0L) sessions.delete(s)
            else sessions.update(s.copy(endedAt = app.db.readings().lastTimeForSession(s.id) ?: s.startedAt))
        }
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
        session?.let { s ->
            if (warning.severity.ordinal > Severity.valueOf(s.maxSeverity).ordinal) {
                val updated = s.copy(maxSeverity = warning.severity.name)
                session = updated
                app.db.sessions().update(updated)
            }
        }
        WarningCardOverlay.show(this, warning, id)
    }

    private suspend fun onEyesWarning(warning: EyesWarning) {
        val id = app.db.events().insert(
            Event(
                time = System.currentTimeMillis(),
                kind = EventKind.WARNING,
                message = warning.message,
                rule = EyesConfig.RULE,
                severity = warning.severity.name,
                durationMs = warning.durationMs,
                demoMode = warning.demoMode,
                signal = Signal.EYES,
                brightnessPercent = warning.brightnessPercent,
                lux = warning.lux,
            )
        )
        WarningCardOverlay.show(this, warning, id)
    }

    private suspend fun log(
        kind: String,
        message: String,
        volumePercent: Int? = null,
        outputType: String? = null,
        signal: String = Signal.EARS,
    ) {
        app.db.events().insert(
            Event(
                time = System.currentTimeMillis(),
                kind = kind,
                message = message,
                volumePercent = volumePercent,
                outputType = outputType,
                demoMode = settings.demoMode,
                signal = signal,
            )
        )
    }

    override fun onDestroy() {
        eyesReader?.setListening(false)
        WarningCardOverlay.remove(this)
        LiveStatus.update { Live(running = false) }
        val demo = settings.demoMode
        val now = System.currentTimeMillis()
        // The loop's scope is cancelled with the service; save the last reading and close the session on the app's scope.
        app.appScope.launch(Dispatchers.Main) {
            flushReading(now)
            closeSession(now)
            app.db.events().insert(
                Event(time = System.currentTimeMillis(), kind = EventKind.MONITORING_STOPPED, message = "monitoring stopped", demoMode = demo)
            )
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MonitoringService"
        const val TICK_MS = 1_000L
        const val READING_MS = 10_000L
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

fun Settings.toEyesConfig() = EyesConfig(
    enabled = eyesEnabled,
    brightnessPercent = eyesBrightnessPercent,
    dimLux = eyesDimLux,
    minutes = eyesMinutes,
    demoMode = demoMode,
)
