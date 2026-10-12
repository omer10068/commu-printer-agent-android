package com.commu.printeragent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commu.printeragent.ui.connection.ConnectionScreen
import com.commu.printeragent.ui.jobs.JobQueueScreen
import com.commu.printeragent.ui.printers.PrinterScreen
import com.commu.printeragent.ui.theme.CommuPrinterAgentTheme
import com.commu.printeragent.ui.theme.IndustrialBorder
import com.commu.printeragent.ui.theme.IndustrialBlue
import com.commu.printeragent.ui.theme.IndustrialDark
import com.commu.printeragent.ui.theme.IndustrialPanel
import com.commu.printeragent.ui.theme.IndustrialTextDim

enum class AppScreen { CONNECTION, PRINTERS, JOBS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CommuPrinterAgentTheme {
                var screen by remember { mutableStateOf(AppScreen.CONNECTION) }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(IndustrialDark)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (screen) {
                            AppScreen.CONNECTION -> ConnectionScreen(
                                modifier = Modifier.fillMaxSize(),
                            )
                            AppScreen.PRINTERS -> PrinterScreen(
                                modifier = Modifier.fillMaxSize(),
                            )
                            AppScreen.JOBS -> JobQueueScreen(
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    AppTabBar(current = screen, onSelect = { screen = it })
                }
            }
        }
    }
}

@Composable
private fun AppTabBar(current: AppScreen, onSelect: (AppScreen) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(IndustrialPanel)
                .border(1.dp, IndustrialBorder),
        ) {
            AppTab(
                label = "1 · חיבור לשרת",
                selected = current == AppScreen.CONNECTION,
                onClick = { onSelect(AppScreen.CONNECTION) },
                modifier = Modifier.weight(1f),
            )
            AppTab(
                label = "2 · מדפסות",
                selected = current == AppScreen.PRINTERS,
                onClick = { onSelect(AppScreen.PRINTERS) },
                modifier = Modifier.weight(1f),
            )
            AppTab(
                label = "3 · תורים",
                selected = current == AppScreen.JOBS,
                onClick = { onSelect(AppScreen.JOBS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AppTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(if (selected) IndustrialBlue.copy(alpha = 0.15f) else Color.Transparent)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) IndustrialBlue else IndustrialTextDim,
        )
    }
}
