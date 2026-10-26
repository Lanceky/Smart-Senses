package com.lanceky.smartsenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanceky.smartsenses.data.Settings
import com.lanceky.smartsenses.ui.components.ConsoleText
import com.lanceky.smartsenses.ui.dashboard.DashboardScreen
import com.lanceky.smartsenses.ui.theme.Black
import com.lanceky.smartsenses.ui.theme.Border
import com.lanceky.smartsenses.ui.theme.Neon
import com.lanceky.smartsenses.ui.theme.TextDim

enum class Tab { DASHBOARD, TRENDS, HISTORY, SETTINGS }

@Composable
fun AppScaffold(settings: Settings) {
    var tab by rememberSaveable { mutableStateOf(Tab.DASHBOARD) }
    Scaffold(
        containerColor = Black,
        bottomBar = { BottomNav(tab) { tab = it } },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.DASHBOARD -> DashboardScreen(settings)
                Tab.TRENDS -> PendingScreen("TRENDS")
                Tab.HISTORY -> PendingScreen("HISTORY")
                Tab.SETTINGS -> PendingScreen("SETTINGS")
            }
        }
    }
}

@Composable
private fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Black).navigationBarsPadding()) {
        HorizontalDivider(color = Border, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().height(56.dp)) {
            Tab.entries.forEach { t ->
                val on = t == selected
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        t.name,
                        color = if (on) Neon else TextDim,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(28.dp).height(2.dp).background(if (on) Neon else Color.Transparent))
                }
            }
        }
    }
}

@Composable
private fun PendingScreen(name: String) {
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        ConsoleText("> $name: arrives in a later build step_", color = TextDim)
    }
}
