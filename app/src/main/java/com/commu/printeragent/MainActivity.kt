package com.commu.printeragent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.commu.printeragent.ui.connection.ConnectionScreen
import com.commu.printeragent.ui.theme.CommuPrinterAgentTheme
import com.commu.printeragent.ui.theme.IndustrialDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CommuPrinterAgentTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = IndustrialDark,
                ) { innerPadding ->
                    ConnectionScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}
