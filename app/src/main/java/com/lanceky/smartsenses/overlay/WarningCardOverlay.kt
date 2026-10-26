package com.lanceky.smartsenses.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lanceky.smartsenses.MainActivity
import com.lanceky.smartsenses.R
import com.lanceky.smartsenses.permissions.Permissions
import com.lanceky.smartsenses.rules.Warning
import com.lanceky.smartsenses.ui.theme.SmartSensesTheme

/** Draws the warning card in its own window over whatever app is open. One card at a time. */
object WarningCardOverlay {
    private const val TAG = "WarningCardOverlay"
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null

    fun show(context: Context, warning: Warning, eventId: Long) {
        // Without Appear on top there's no card; the warning is already recorded.
        if (!Permissions.canDrawOverlays(context)) return
        remove(context)

        val windowManager = context.getSystemService(WindowManager::class.java)
        val owner = OverlayOwner().also { it.start() }
        val card = ComposeView(ContextThemeWrapper(context, R.style.Theme_SmartSenses))
        card.setViewTreeLifecycleOwner(owner)
        card.setViewTreeSavedStateRegistryOwner(owner)
        card.setContent {
            SmartSensesTheme {
                WarningCard(
                    warning = warning,
                    onTap = {
                        removeIfCurrent(context, card)
                        openApp(context, eventId)
                    },
                    onGone = { removeIfCurrent(context, card) },
                )
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable: touches outside the card reach the app underneath, and the keyboard is never taken.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            title = "Smart-Senses warning card"
        }
        try {
            windowManager.addView(card, params)
            view = card
            this.owner = owner
        } catch (e: Exception) {
            Log.w(TAG, "could not add the card window", e)
            owner.destroy()
        }
    }

    fun remove(context: Context) {
        val current = view ?: return
        view = null
        try {
            context.getSystemService(WindowManager::class.java).removeView(current)
        } catch (e: Exception) {
            Log.w(TAG, "could not remove the card window", e)
        }
        owner?.destroy()
        owner = null
    }

    private fun removeIfCurrent(context: Context, card: ComposeView) {
        if (view === card) remove(context)
    }

    private fun openApp(context: Context, eventId: Long) {
        // Allowed from the background because the app holds Appear on top and its window is visible.
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_WARNING_ID, eventId)
        )
    }
}
