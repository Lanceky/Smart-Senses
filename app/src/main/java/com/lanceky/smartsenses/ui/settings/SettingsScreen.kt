package com.lanceky.smartsenses.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanceky.smartsenses.SmartSensesApp
import com.lanceky.smartsenses.data.Event
import com.lanceky.smartsenses.data.EventKind
import com.lanceky.smartsenses.data.Settings
import com.lanceky.smartsenses.monitor.MonitoringService
import com.lanceky.smartsenses.rules.EarsConfig
import com.lanceky.smartsenses.ui.components.CapsLabel
import com.lanceky.smartsenses.ui.components.ConsoleButton
import com.lanceky.smartsenses.ui.components.ConsolePanel
import com.lanceky.smartsenses.ui.components.ConsoleText
import com.lanceky.smartsenses.ui.theme.Amber
import com.lanceky.smartsenses.ui.theme.Black
import com.lanceky.smartsenses.ui.theme.Border
import com.lanceky.smartsenses.ui.theme.Neon
import com.lanceky.smartsenses.ui.theme.PanelRaised
import com.lanceky.smartsenses.ui.theme.TextDim
import com.lanceky.smartsenses.ui.theme.TextMain
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: Settings) {
    val context = LocalContext.current
    val app = context.applicationContext as SmartSensesApp
    val store = app.settingsStore
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CapsLabel("SETTINGS", color = Neon, size = 15.sp, bold = true)

        ConsolePanel("MONITORING") {
            ToggleRow("Ears monitoring", settings.monitoringEnabled) { on ->
                scope.launch {
                    store.setMonitoringEnabled(on)
                    if (on) MonitoringService.start(context, MonitoringService.REASON_USER) else MonitoringService.stop(context)
                }
            }
            ConsoleText("Watches headphone volume in the background, even while other apps are open.", color = TextDim, size = 12.sp)
        }

        ConsolePanel("EARS / WARNING THRESHOLDS") {
            Stepper("High-volume threshold", "${settings.thresholdPercent}%",
                onMinus = { scope.launch { store.setThresholdPercent(settings.thresholdPercent - 5) } },
                onPlus = { scope.launch { store.setThresholdPercent(settings.thresholdPercent + 5) } })
            Stepper("Continuous warning", "${settings.continuousMinutes} min",
                onMinus = { scope.launch { store.setContinuousMinutes(settings.continuousMinutes - 5) } },
                onPlus = { scope.launch { store.setContinuousMinutes(settings.continuousMinutes + 5) } })
            Stepper("Daily cumulative warning", "${settings.dailyMinutes} min",
                onMinus = { scope.launch { store.setDailyMinutes(settings.dailyMinutes - 15) } },
                onPlus = { scope.launch { store.setDailyMinutes(settings.dailyMinutes + 15) } })
            FixedRow("Very high (red)", "≥ ${EarsConfig.VERY_HIGH_PERCENT}% for ${EarsConfig.VERY_HIGH_MINUTES} min")
            FixedRow("Demo duration", "${EarsConfig.DEMO_CONTINUOUS_MINUTES} min")
            Spacer(Modifier.height(6.dp))
            ConsoleText(
                "${EarsConfig.COOLDOWN_MINUTES}-minute cooldown per warning type · at most ${EarsConfig.DAILY_CAP} cards a day. " +
                    "Paused music doesn't count.",
                color = TextDim, size = 12.sp,
            )
        }

        ConsolePanel("DEMO MODE", accent = Amber) {
            ToggleRow("Demo mode", settings.demoMode, accent = Amber) { on ->
                scope.launch {
                    store.setDemoMode(on)
                    if (on) {
                        app.db.events().insert(
                            Event(
                                time = System.currentTimeMillis(),
                                kind = EventKind.DEMO_MODE_ON,
                                message = "demo mode on: continuous warning after 1 min; cooldowns and today's card count reset",
                                demoMode = true,
                            )
                        )
                    }
                }
            }
            ConsoleText(
                "Shortens only the continuous warning to 1 minute, so you can see a real warning quickly. " +
                    "Headphones and the threshold still apply. Turning it on resets cooldowns and today's card count. Nothing is wiped.",
                color = TextDim, size = 12.sp,
            )
        }

        ConsolePanel("ABOUT") {
            ConsoleText("Readings stay on this phone. No account, no internet.", size = 12.sp)
            Spacer(Modifier.height(6.dp))
            ConsoleText(
                "Volume % is your phone's media-volume setting. It's an estimate of exposure, not a decibel measurement: " +
                    "the same setting can be louder on different headphones. Not medical advice.",
                color = TextDim, size = 12.sp,
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, accent: Color = Neon, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            ConsoleText(label, size = 14.sp)
            CapsLabel(if (checked) "ON" else "OFF", color = if (checked) accent else TextDim, bold = true)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Black,
                checkedTrackColor = accent,
                checkedBorderColor = accent,
                uncheckedThumbColor = TextDim,
                uncheckedTrackColor = PanelRaised,
                uncheckedBorderColor = Border,
            ),
        )
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ConsoleText(label, Modifier.weight(1f), size = 13.sp)
        ConsoleButton("−", onMinus)
        Text(
            value,
            Modifier.widthIn(min = 72.dp),
            color = TextMain,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        )
        ConsoleButton("+", onPlus)
    }
}

@Composable
private fun FixedRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ConsoleText(label, Modifier.weight(1f), color = TextDim, size = 13.sp)
        ConsoleText(value, color = TextDim, size = 13.sp)
        Spacer(Modifier.width(8.dp))
        CapsLabel("fixed")
    }
}
