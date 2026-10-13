package com.commu.printeragent.ui.onboarding.step2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.commu.printeragent.ui.onboarding.WizardViewModel
import com.commu.printeragent.ui.printers.PrinterViewModel
import com.commu.printeragent.ui.printers.SyncStatus
import com.commu.printeragent.ui.theme.IndustrialAmber
import com.commu.printeragent.ui.theme.IndustrialBlue
import com.commu.printeragent.ui.theme.IndustrialBorder
import com.commu.printeragent.ui.theme.IndustrialBorderActive
import com.commu.printeragent.ui.theme.IndustrialDark
import com.commu.printeragent.ui.theme.IndustrialGreen
import com.commu.printeragent.ui.theme.IndustrialPanel
import com.commu.printeragent.ui.theme.IndustrialRed
import com.commu.printeragent.ui.theme.IndustrialSurface
import com.commu.printeragent.ui.theme.IndustrialText
import com.commu.printeragent.ui.theme.IndustrialTextDim
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step2DiscoveryContent(
    wizardVm: WizardViewModel,
    printerVm: PrinterViewModel = viewModel(),
) {
    val state by printerVm.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var showSheet by remember { mutableStateOf(false) }
    var selectedIp by remember { mutableStateOf("") }
    var sheetName by remember { mutableStateOf("") }
    var sheetError by remember { mutableStateOf<String?>(null) }
    var hasScanned by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Mark step 2 complete when printers are saved and not scanning
    LaunchedEffect(state.savedPrinters.isNotEmpty(), state.isScanning) {
        if (state.savedPrinters.isNotEmpty() && !state.isScanning) {
            wizardVm.markStep2Complete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IndustrialDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Section header
        Step2SectionHeader()

        Spacer(Modifier.height(6.dp))

        Text(
            text = "לחץ + ליד כל מדפסת שהקופה צריכה, ותן לה שם.",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = IndustrialTextDim,
        )

        Spacer(Modifier.height(12.dp))

        // Scan button
        Button(
            onClick = {
                hasScanned = true
                printerVm.startScan()
            },
            enabled = !state.isScanning,
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = IndustrialBlue,
                contentColor = Color.White,
                disabledContainerColor = IndustrialSurface,
                disabledContentColor = IndustrialTextDim,
            ),
        ) {
            Text(
                if (hasScanned) "חפש שוב" else "חפש מדפסות ברשת",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }

        // Scan progress
        if (state.isScanning) {
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = IndustrialBlue,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "סורק את הרשת · ${state.scanProgress}/${state.scanTotal}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = IndustrialTextDim,
                )
            }
            Spacer(Modifier.height(6.dp))
            if (state.scanTotal > 0) {
                LinearProgressIndicator(
                    progress = { state.scanProgress.toFloat() / state.scanTotal.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = IndustrialBlue,
                    trackColor = IndustrialSurface,
                )
            }
        }

        // No results after scan
        if (hasScanned && !state.isScanning && state.scanCandidates.isEmpty() && state.savedPrinters.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "לא נמצאו מדפסות — ודא שהן דלוקות ובאותו ראוטר",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = IndustrialAmber,
            )
        }

        // Scan candidates
        if (state.scanCandidates.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "נמצאו ${state.scanCandidates.size} מועמדים:",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = IndustrialTextDim,
            )
            Spacer(Modifier.height(6.dp))

            state.scanCandidates.forEach { ip ->
                val savedPrinter = state.savedPrinters.find { it.address == ip }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IndustrialSurface)
                        .border(1.dp, IndustrialBorder)
                        .clickable(enabled = savedPrinter == null) {
                            selectedIp = ip
                            sheetName = ""
                            sheetError = null
                            showSheet = true
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = ip,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = IndustrialText,
                        )
                    }
                    if (savedPrinter != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = savedPrinter.name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = IndustrialTextDim,
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(IndustrialGreen.copy(alpha = 0.15f))
                                    .border(1.dp, IndustrialGreen.copy(alpha = 0.4f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "שמורה",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = IndustrialGreen,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "⊕",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            color = IndustrialBlue,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }

        // Saved printers list
        if (state.savedPrinters.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "מדפסות שמורות (${state.savedPrinters.size}):",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = IndustrialTextDim,
            )
            Spacer(Modifier.height(6.dp))

            state.savedPrinters.forEach { printer ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IndustrialSurface)
                        .border(1.dp, IndustrialBorder)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = printer.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = IndustrialText,
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = "${printer.address}:${printer.port}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = IndustrialTextDim,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(IndustrialGreen.copy(alpha = 0.15f))
                            .border(1.dp, IndustrialGreen.copy(alpha = 0.4f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "שמורה ✓",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = IndustrialGreen,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }

        // Sync status
        if (state.isSyncing || state.syncStatus !is SyncStatus.Idle) {
            Spacer(Modifier.height(8.dp))
            val (syncColor, syncText) = when (val s = state.syncStatus) {
                is SyncStatus.Idle -> Pair(IndustrialTextDim, "")
                is SyncStatus.Success -> Pair(IndustrialGreen, "✓ ${s.message}")
                is SyncStatus.Partial -> Pair(IndustrialAmber, "⚠ ${s.message}")
                is SyncStatus.Failure -> Pair(IndustrialRed, "✗ ${s.message}")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = IndustrialBlue,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "מסנכרן עם השרת…",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = IndustrialTextDim,
                    )
                } else if (syncText.isNotEmpty()) {
                    Text(
                        text = syncText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = syncColor,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    // Bottom sheet for saving a printer
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = IndustrialPanel,
            contentColor = IndustrialText,
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = "שמירת מדפסת",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndustrialText,
                    )
                    Spacer(Modifier.height(4.dp))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = selectedIp,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = IndustrialTextDim,
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "שם המדפסת",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = IndustrialTextDim,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = sheetName,
                        onValueChange = { sheetName = it; sheetError = null },
                        placeholder = {
                            Text(
                                "לדוגמה: קופה ראשית",
                                fontFamily = FontFamily.Monospace,
                                color = IndustrialTextDim,
                                fontSize = 13.sp,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RectangleShape,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = IndustrialText,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = IndustrialText,
                            unfocusedTextColor = IndustrialText,
                            focusedContainerColor = IndustrialSurface,
                            unfocusedContainerColor = IndustrialSurface,
                            focusedBorderColor = IndustrialBorderActive,
                            unfocusedBorderColor = IndustrialBorder,
                            cursorColor = IndustrialBlue,
                            focusedPlaceholderColor = IndustrialTextDim,
                            unfocusedPlaceholderColor = IndustrialTextDim,
                        ),
                    )

                    if (sheetError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = sheetError!!,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = IndustrialRed,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = { showSheet = false },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                "ביטול",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = IndustrialTextDim,
                            )
                        }
                        Button(
                            onClick = {
                                if (sheetName.isBlank()) {
                                    sheetError = "חובה לתת שם למדפסת"
                                    return@Button
                                }
                                printerVm.updateFormAddress(selectedIp)
                                printerVm.updateFormName(sheetName.trim())
                                printerVm.savePrinter()
                                showSheet = false
                                scope.launch {
                                    printerVm.syncWithBackend()
                                }
                            },
                            modifier = Modifier.weight(2f),
                            shape = RectangleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IndustrialBlue,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(
                                "שמור",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun Step2SectionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IndustrialSurface)
            .border(1.dp, IndustrialBorder),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(IndustrialBlue)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "2",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "איתור מדפסות",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = IndustrialText,
        )
    }
}
