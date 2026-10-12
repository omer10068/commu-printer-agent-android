package com.commu.printeragent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val IndustrialColorScheme = darkColorScheme(
    primary = IndustrialBlue,
    onPrimary = IndustrialText,
    secondary = IndustrialCyan,
    onSecondary = IndustrialDark,
    background = IndustrialDark,
    onBackground = IndustrialText,
    surface = IndustrialSurface,
    onSurface = IndustrialText,
    onSurfaceVariant = IndustrialTextDim,
    error = IndustrialRed,
    onError = IndustrialText,
    outline = IndustrialBorder,
)

@Composable
fun CommuPrinterAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IndustrialColorScheme,
        typography = Typography,
        content = content,
    )
}
