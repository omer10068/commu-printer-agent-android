package com.commu.printeragent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.commu.printeragent.data.AppSettings
import com.commu.printeragent.data.OnboardingRepository
import com.commu.printeragent.data.PrinterMappingRepository
import com.commu.printeragent.data.PrinterRepository
import com.commu.printeragent.data.SecureTokenStorage
import com.commu.printeragent.ui.dashboard.DashboardScreen
import com.commu.printeragent.ui.onboarding.WizardScreen
import com.commu.printeragent.ui.theme.CommuPrinterAgentTheme
import com.commu.printeragent.ui.theme.IndustrialBlue
import com.commu.printeragent.ui.theme.IndustrialDark

// Retained for any external references that may still use this enum
enum class AppScreen { CONNECTION, PRINTERS, JOBS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val onboardingRepo = OnboardingRepository(this)
        setContent {
            CommuPrinterAgentTheme {
                var migrationDone by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    onboardingRepo.runMigrationIfNeeded(
                        AppSettings(this@MainActivity),
                        SecureTokenStorage(this@MainActivity),
                        PrinterRepository(this@MainActivity),
                        PrinterMappingRepository(this@MainActivity),
                    )
                    migrationDone = true
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(IndustrialDark)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    if (!migrationDone) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = IndustrialBlue)
                        }
                    } else {
                        val setupComplete by onboardingRepo.setupCompleteFlow.collectAsState(initial = null)
                        when (setupComplete) {
                            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = IndustrialBlue)
                            }
                            false -> WizardScreen(modifier = Modifier.fillMaxSize())
                            true  -> DashboardScreen(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}
