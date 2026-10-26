package com.lanceky.smartsenses.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lanceky.smartsenses.SmartSensesApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Resumes monitoring after a phone restart or an app update, if the user had it on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext as SmartSensesApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = app.settingsStore.settings.first()
                if (s.onboardingDone && s.monitoringEnabled) {
                    val reason = if (intent.action == Intent.ACTION_BOOT_COMPLETED) MonitoringService.REASON_BOOT else REASON_UPDATE
                    MonitoringService.start(context.applicationContext, reason)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val REASON_UPDATE = "app update"
    }
}
