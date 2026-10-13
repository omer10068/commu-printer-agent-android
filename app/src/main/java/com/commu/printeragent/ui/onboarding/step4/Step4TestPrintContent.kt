package com.commu.printeragent.ui.onboarding.step4

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.commu.printeragent.data.PrinterRecord
import com.commu.printeragent.data.TestPrintStatus
import com.commu.printeragent.ui.theme.IndustrialAmber
import com.commu.printeragent.ui.theme.IndustrialBlue
import com.commu.printeragent.ui.theme.IndustrialBorder
import com.commu.printeragent.ui.theme.IndustrialDark
import com.commu.printeragent.ui.theme.IndustrialGreen
import com.commu.printeragent.ui.theme.IndustrialPanel
import com.commu.printeragent.ui.theme.IndustrialRed
import com.commu.printeragent.ui.theme.IndustrialSurface
import com.commu.printeragent.ui.theme.IndustrialText
import com.commu.printeragent.ui.theme.IndustrialTextDim
import kotlinx.coroutines.delay

@Composable
fun Step4TestPrintContent(
    vm: Step4ViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()

    // Auto-dismiss toast after 2 seconds
    LaunchedEffect(state.toastMessage) {
        if (state.toastMessage != null) {
            delay(2000)
            vm.clearToast()
        }
    }

    // Physical confirmation dialog
    val dialogPrinterId = state.showingDialogForPrinterId
    if (dialogPrinterId != null) {
        AlertDialog(
            onDismissRequest = { /* user must choose */ },
            title = {
                Text(
                    text = "המדפסת הדפיסה בהצלחה?",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = IndustrialText,
                )
            },
            text = {
                Text(
                    text = "בדוק שיצא נייר פיזי מהמדפסת",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = IndustrialTextDim,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.onPhysicalConfirm(dialogPrinterId, true) }) {
                    Text(
                        "כן",
                        fontFamily = FontFamily.Monospace,
                        color = IndustrialGreen,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.onPhysicalConfirm(dialogPrinterId, false) }) {
                    Text(
                        "לא",
                        fontFamily = FontFamily.Monospace,
                        color = IndustrialRed,
                    )
                }
            },
            containerColor = IndustrialPanel,
            titleContentColor = IndustrialText,
            textContentColor = IndustrialTextDim,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(IndustrialDark)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Section header
            Step4SectionHeader()

            Spacer(Modifier.height(6.dp))

            Text(
                text = "לחץ על מדפסת כדי להדפיס דף בדיקה.",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = IndustrialTextDim,
            )

            Spacer(Modifier.height(12.dp))

            if (state.printers.isEmpty()) {
                Text(
                    text = "אין מדפסות שמורות — חזור לשלב 2 והוסף מדפסות",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = IndustrialAmber,
                )
            } else {
                state.printers.forEach { printer ->
                    val rowStatus = state.rowStatus[printer.id] ?: PrintRowStatus.Idle
                    val isPrinting = rowStatus == PrintRowStatus.Printing

                    PrinterTestRow(
                        printer = printer,
                        rowStatus = rowStatus,
                        onClick = {
                            if (!isPrinting) vm.sendTestPrint(printer)
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // Toast overlay
        if (state.toastMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .background(IndustrialPanel)
                    .border(1.dp, IndustrialBorder)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = state.toastMessage!!,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = IndustrialText,
                )
            }
        }
    }
}

@Composable
private fun PrinterTestRow(
    printer: PrinterRecord,
    rowStatus: PrintRowStatus,
    onClick: () -> Unit,
) {
    val isPrinting = rowStatus == PrintRowStatus.Printing

    val borderColor = when {
        printer.testPrintStatus == TestPrintStatus.Verified -> IndustrialGreen.copy(alpha = 0.4f)
        printer.testPrintStatus == TestPrintStatus.Failed   -> IndustrialRed.copy(alpha = 0.4f)
        rowStatus == PrintRowStatus.TcpFailed               -> IndustrialAmber.copy(alpha = 0.4f)
        else                                                -> IndustrialBorder
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IndustrialSurface)
            .border(1.dp, borderColor)
            .clickable(enabled = !isPrinting, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = printer.name,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
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

        when {
            isPrinting -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = IndustrialBlue,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "מדפיס…",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = IndustrialBlue,
                    )
                }
            }
            printer.testPrintStatus == TestPrintStatus.Verified -> {
                Text(
                    text = "תקינה ✓",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = IndustrialGreen,
                )
            }
            printer.testPrintStatus == TestPrintStatus.Failed -> {
                Text(
                    text = "לא הודפס — לחץ לנסות שוב",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = IndustrialRed,
                )
            }
            rowStatus == PrintRowStatus.TcpFailed -> {
                Text(
                    text = "לא הגיבה — לחץ לנסות שוב",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = IndustrialAmber,
                )
            }
            else -> {
                Text(
                    text = "לא נבדקה עדיין",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = IndustrialTextDim,
                )
            }
        }
    }
}

@Composable
private fun Step4SectionHeader() {
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
                text = "4",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "הדפסת בדיקה",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = IndustrialText,
        )
    }
}
