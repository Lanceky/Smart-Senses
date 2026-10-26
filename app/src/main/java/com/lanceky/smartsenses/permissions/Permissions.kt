package com.lanceky.smartsenses.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

data class PermissionStatus(val overlay: Boolean, val notifications: Boolean)

/** Status checks and the Settings pages for the two permissions the user grants. */
object Permissions {
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** On Android 13+ this is false until POST_NOTIFICATIONS is granted. */
    fun notificationsGranted(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun status(context: Context) = PermissionStatus(canDrawOverlays(context), notificationsGranted(context))

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}

/** Re-checks every time the screen comes back, e.g. after the user returns from a Settings page. */
@Composable
fun rememberPermissionStatus(): PermissionStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(Permissions.status(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = Permissions.status(context) }
    return status
}
