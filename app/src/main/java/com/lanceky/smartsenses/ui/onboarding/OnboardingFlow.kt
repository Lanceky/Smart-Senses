package com.lanceky.smartsenses.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanceky.smartsenses.R
import com.lanceky.smartsenses.SmartSensesApp
import com.lanceky.smartsenses.monitor.MonitoringService
import com.lanceky.smartsenses.permissions.Permissions
import com.lanceky.smartsenses.permissions.rememberPermissionStatus
import com.lanceky.smartsenses.ui.components.CapsLabel
import com.lanceky.smartsenses.ui.components.ConsoleButton
import com.lanceky.smartsenses.ui.components.ConsolePanel
import com.lanceky.smartsenses.ui.components.ConsoleText
import com.lanceky.smartsenses.ui.theme.Amber
import com.lanceky.smartsenses.ui.theme.Black
import com.lanceky.smartsenses.ui.theme.Border
import com.lanceky.smartsenses.ui.theme.Neon
import com.lanceky.smartsenses.ui.theme.TextDim
import com.lanceky.smartsenses.ui.theme.TextMain
import kotlinx.coroutines.launch

private enum class Step { WELCOME, CHOOSE, NOTIFICATIONS, OVERLAY, CHECKLIST }

/**
 * First-time setup. Each permission is explained right before it's asked for, every permission
 * step can be skipped, and Finish starts monitoring and opens the dashboard.
 */
@Composable
fun OnboardingFlow() {
    val context = LocalContext.current
    val app = context.applicationContext as SmartSensesApp
    val scope = rememberCoroutineScope()
    val steps = remember { Step.entries.filter { it != Step.NOTIFICATIONS || Build.VERSION.SDK_INT >= 33 } }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var earsOn by rememberSaveable { mutableStateOf(true) }
    var eyesOn by rememberSaveable { mutableStateOf(false) }
    val next = { index = (index + 1).coerceAtMost(steps.lastIndex) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (steps[index] != Step.WELCOME) {
            CapsLabel("setup ${index}/${steps.lastIndex}", color = TextDim, size = 10.sp)
        }
        when (steps[index]) {
            Step.WELCOME -> Welcome(next)
            Step.CHOOSE -> Choose(earsOn, { earsOn = it }, eyesOn, { eyesOn = it }, next)
            Step.NOTIFICATIONS -> NotificationsStep(next)
            Step.OVERLAY -> OverlayStep(next)
            Step.CHECKLIST -> Checklist(earsOn, eyesOn && earsOn) {
                scope.launch {
                    app.settingsStore.setEyesEnabled(eyesOn && earsOn)
                    app.settingsStore.setMonitoringEnabled(earsOn)
                    if (earsOn) MonitoringService.start(context, MonitoringService.REASON_USER)
                    app.settingsStore.setOnboardingDone(true)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Welcome(onStart: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    Image(
        painterResource(R.drawable.ic_logo),
        contentDescription = "Smart-Senses logo: an eye made of circuit lines with a sound-wave pupil",
        modifier = Modifier.size(132.dp).align(Alignment.CenterHorizontally),
    )
    Text(
        "SMART-SENSES",
        Modifier.align(Alignment.CenterHorizontally),
        color = Neon, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontFamily = FontFamily.Monospace,
    )
    ConsoleText(
        "Your device. Your habits. Your senses.\nStay aware before small habits become big problems.",
        Modifier.fillMaxWidth(),
        color = TextMain, size = 14.sp,
    )
    ConsoleText(
        "> readings stay on this phone. no account, no cloud._",
        color = TextDim, size = 12.sp,
    )
    Spacer(Modifier.height(12.dp))
    ConsoleButton("get started", onStart, Modifier.fillMaxWidth(), filled = true)
}

@Composable
private fun Choose(earsOn: Boolean, onEars: (Boolean) -> Unit, eyesOn: Boolean, onEyes: (Boolean) -> Unit, onNext: () -> Unit) {
    Title("Choose what to monitor")
    ConsolePanel("EARS · HEADPHONE VOLUME", accent = Neon, glow = if (earsOn) Neon else null, trailing = {
        Switch(
            checked = earsOn,
            onCheckedChange = onEars,
            colors = SwitchDefaults.colors(checkedTrackColor = Neon, checkedThumbColor = Black, uncheckedTrackColor = Black, uncheckedBorderColor = Border),
        )
    }) {
        ConsoleText("Warns you with a card when headphone volume stays high for too long.", size = 13.sp)
    }
    ConsolePanel("EYES · SCREEN BRIGHTNESS", glow = if (eyesOn && earsOn) Neon else null, trailing = {
        Switch(
            checked = eyesOn && earsOn,
            onCheckedChange = onEyes,
            enabled = earsOn,
            colors = SwitchDefaults.colors(checkedTrackColor = Neon, checkedThumbColor = Black, uncheckedTrackColor = Black, uncheckedBorderColor = Border),
        )
    }) {
        ConsoleText("Spots a bright screen in a dim room, especially late at night. Optional: you can turn it on later.", size = 13.sp)
        if (!earsOn) ConsoleText("Runs with monitoring: turn Ears on first.", color = TextDim, size = 12.sp)
    }
    ComingLater("DEVICE · TEMPERATURE", "Will notice when the phone keeps running hot, especially while charging.")
    ConsoleButton("continue", onNext, Modifier.fillMaxWidth(), filled = true)
}

@Composable
private fun ComingLater(title: String, body: String) {
    ConsolePanel(title, trailing = { CapsLabel("coming later", color = TextDim, size = 10.sp) }) {
        ConsoleText(body, color = TextDim, size = 12.sp)
    }
}

@Composable
private fun NotificationsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val status = rememberPermissionStatus()
    // Granted or denied, setup moves on: a denied prompt is never a dead end.
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onNext() }
    Title("Notifications")
    Explainer(
        "Smart-Senses watches in the background, and Android requires an ongoing notification for that. " +
            "It shows you that monitoring is on. Without it, monitoring still runs; you just won't see the notice.",
    )
    StatusLine("NOTIFICATIONS", status.notifications)
    if (status.notifications) {
        ConsoleButton("continue", onNext, Modifier.fillMaxWidth(), filled = true)
    } else {
        ConsoleButton("allow notifications", {
            if (Build.VERSION.SDK_INT >= 33) request.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(Permissions.notificationSettingsIntent(context))
        }, Modifier.fillMaxWidth(), filled = true)
        Skip(onNext)
    }
}

@Composable
private fun OverlayStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val status = rememberPermissionStatus()
    Title("Appear on top")
    Explainer(
        "The warning card slides over the app you're using, like your music app. Android calls this " +
            "\"Appear on top\". Smart-Senses uses it only for the card. Without it, warnings are still recorded " +
            "in History, but no card appears.",
    )
    ConsoleText(
        "> continue to settings opens Android's Appear on top list: find Smart-Senses, switch it on, then come back._",
        color = TextDim, size = 12.sp,
    )
    StatusLine("APPEAR ON TOP", status.overlay)
    if (status.overlay) {
        ConsoleButton("continue", onNext, Modifier.fillMaxWidth(), filled = true)
    } else {
        ConsoleButton("continue to settings", {
            context.startActivity(Permissions.overlaySettingsIntent(context))
        }, Modifier.fillMaxWidth(), filled = true)
        Skip(onNext)
    }
}

@Composable
private fun Checklist(earsOn: Boolean, eyesOn: Boolean, onFinish: () -> Unit) {
    val context = LocalContext.current
    val status = rememberPermissionStatus()
    Title("Setup checklist")
    ConsolePanel("READY CHECK") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckRow("EARS", if (earsOn) "ON" else "OFF", earsOn)
            CheckRow("EYES", if (eyesOn) "ON" else "OFF · turn on any time in Settings", true)
            CheckRow("NOTIFICATIONS", if (status.notifications) "GRANTED" else "MISSING", status.notifications) {
                context.startActivity(Permissions.notificationSettingsIntent(context))
            }
            CheckRow(
                "APPEAR ON TOP",
                if (status.overlay) "GRANTED" else "MISSING · warning card unavailable",
                status.overlay,
            ) { context.startActivity(Permissions.overlaySettingsIntent(context)) }
            CheckRow("MONITORING", if (earsOn) "will start" else "off · Ears is off", earsOn)
        }
    }
    if (!status.notifications || !status.overlay) {
        ConsoleText("You can fix missing items now or later from the dashboard.", color = TextDim, size = 12.sp)
    }
    ConsoleButton("finish", onFinish, Modifier.fillMaxWidth(), filled = true)
}

@Composable
private fun CheckRow(label: String, value: String, ok: Boolean, onFix: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "[✓]" else "[!]", color = if (ok) Neon else Amber, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(8.dp))
        ConsoleText("$label: ${value.uppercase()}", Modifier.weight(1f), color = if (ok) TextMain else Amber, size = 13.sp)
        if (!ok && onFix != null) {
            Text(
                "[ FIX ]",
                Modifier.clickable(onClick = onFix).padding(4.dp),
                color = Amber, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text.uppercase(), color = Neon, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
}

@Composable
private fun Explainer(text: String) {
    ConsoleText(text, color = TextMain, size = 14.sp)
}

@Composable
private fun StatusLine(label: String, granted: Boolean) {
    CapsLabel("$label: ${if (granted) "granted" else "missing"}", color = if (granted) Neon else Amber, bold = true)
}

@Composable
private fun Skip(onSkip: () -> Unit) {
    Text(
        "Skip for now",
        Modifier.fillMaxWidth().clickable(onClick = onSkip).padding(vertical = 10.dp),
        color = TextDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
    )
}
