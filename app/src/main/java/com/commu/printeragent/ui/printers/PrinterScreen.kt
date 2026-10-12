package com.commu.printeragent.ui.printers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.commu.printeragent.data.PrinterRecord
import com.commu.printeragent.printing.LocalNetworkInfo
import com.commu.printeragent.printing.formatCheckTime
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

@Composable
fun PrinterScreen(
    modifier: Modifier = Modifier,
    vm: PrinterViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier
                .background(IndustrialDark)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // ── App title bar ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IndustrialPanel)
                    .border(1.dp, IndustrialBorder)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "COMMU PRINTER AGENT  v1.0",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = IndustrialTextDim,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "● מדפסות",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = IndustrialBlue,
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Global status banner ─────────────────────────────────────────
            state.statusMessage?.let { msg ->
                StatusBanner(msg) { vm.clearStatus() }
                Spacer(Modifier.height(8.dp))
            }

            // ── Section [2]: Network info + scan ──────────────────────────────
            PrinterSectionHeader(number = "2", title = "גילוי מדפסות ברשת")
            Spacer(Modifier.height(8.dp))
            NetworkInfoPanel(info = state.localNetwork, onRefresh = { vm.refreshNetworkInfo() })
            Spacer(Modifier.height(8.dp))
            ScanControls(
                isScanning = state.isScanning,
                progress = state.scanProgress,
                total = state.scanTotal,
                onScan = { vm.startScan() },
                onCancel = { vm.cancelScan() },
            )
            if (state.scanCandidates.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ScanCandidateList(
                    candidates = state.scanCandidates,
                    savedAddresses = state.savedPrinters.map { it.address }.toSet(),
                    onAdd = { ip -> vm.prepopulateForm(ip) },
                )
            } else if (!state.isScanning && state.scanProgress > 0) {
                Spacer(Modifier.height(6.dp))
                MonoText("לא נמצאו מכשירים פתוחים בפורט 9100 ברשת הנוכחית", IndustrialTextDim)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = IndustrialBorder)
            Spacer(Modifier.height(12.dp))

            // ── Section [3]: Add / Edit form ──────────────────────────────────
            PrinterSectionHeader(
                number = "3",
                title = if (state.editingId != null) "ערוך מדפסת" else "הוסף מדפסת",
            )
            Spacer(Modifier.height(8.dp))
            PrinterForm(state = state, vm = vm)

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = IndustrialBorder)
            Spacer(Modifier.height(12.dp))

            // ── Section [4]: Saved printers ───────────────────────────────────
            PrinterSectionHeader(number = "4", title = "מדפסות שמורות")
            Spacer(Modifier.height(8.dp))
            if (!state.isPrintersLoading && state.loadError != null) {
                MonoText("⚠ ${state.loadError}", IndustrialRed)
                Spacer(Modifier.height(6.dp))
            }
            when {
                state.isPrintersLoading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = IndustrialTextDim,
                        )
                    }
                }
                state.savedPrinters.isEmpty() -> {
                    MonoText(
                        "אין מדפסות שמורות — הוסף מדפסת ידנית בסעיף 3 או דרך סריקת הרשת",
                        IndustrialTextDim,
                    )
                }
                else -> {
                    state.savedPrinters.forEach { printer ->
                        PrinterCard(
                            printer = printer,
                            isChecking = state.checkingIds.contains(printer.id),
                            onTest = { vm.testPrinter(printer.id) },
                            onEdit = { vm.startEdit(printer) },
                            onDelete = { vm.deletePrinter(printer.id) },
                            onSetPrimary = { vm.setPrimary(printer.id) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    MonoText(
                        "${state.savedPrinters.size} מדפסות שמורות",
                        IndustrialTextDim,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Network info panel ────────────────────────────────────────────────────────

@Composable
private fun NetworkInfoPanel(info: LocalNetworkInfo?, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IndustrialSurface)
            .border(1.dp, IndustrialBorder)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (info != null) {
                MonoText("כתובת: ${info.address}  /  ${info.prefixLength}", IndustrialText)
                MonoText(
                    "רשת: ${info.effectivePrefix}-bit · ${info.scanHostCount} מארחים לסריקה",
                    IndustrialTextDim,
                )
            } else {
                MonoText("⚠ לא נמצא ממשק רשת פעיל — בדוק חיבור Wi-Fi", IndustrialAmber)
            }
        }
        TextButton(onClick = onRefresh) {
            Text(
                "[ רענן ]",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = IndustrialBlue,
            )
        }
    }
}

// ── Scan controls ─────────────────────────────────────────────────────────────

@Composable
private fun ScanControls(
    isScanning: Boolean,
    progress: Int,
    total: Int,
    onScan: () -> Unit,
    onCancel: () -> Unit,
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isScanning) {
                Button(
                    onClick = onScan,
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IndustrialBlue,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("סרוק פורט TCP 9100", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            } else {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RectangleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = IndustrialAmber),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndustrialAmber),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = IndustrialAmber,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("עצור סריקה", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }

        if (isScanning || (total > 0 && progress < total)) {
            Spacer(Modifier.height(6.dp))
            val fraction = if (total > 0) progress.toFloat() / total.toFloat() else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(IndustrialSurface),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(IndustrialBlue),
                )
            }
            Spacer(Modifier.height(4.dp))
            MonoText("$progress / $total", IndustrialTextDim)
        }
    }
}

// ── Scan candidate list ───────────────────────────────────────────────────────

@Composable
private fun ScanCandidateList(
    candidates: List<String>,
    savedAddresses: Set<String>,
    onAdd: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IndustrialSurface)
            .border(1.dp, IndustrialBorder)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MonoText("מועמדים שנמצאו (פורט 9100 פתוח):", IndustrialTextDim)
        candidates.take(30).forEach { ip ->
            val alreadySaved = ip in savedAddresses
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        ip,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = IndustrialText,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (alreadySaved) {
                    Text(
                        "[ שמור ]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = IndustrialGreen,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                } else {
                    TextButton(
                        onClick = { onAdd(ip) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                    ) {
                        Text(
                            "[ הוסף ]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = IndustrialBlue,
                        )
                    }
                }
            }
        }
        if (candidates.size > 30) {
            MonoText("... ו-${candidates.size - 30} נוספים", IndustrialTextDim)
        }
    }
}

// ── Add / Edit form ───────────────────────────────────────────────────────────

@Composable
private fun PrinterForm(state: PrinterUiState, vm: PrinterViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // IP Address (LTR)
        FieldLabel("כתובת IP")
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = state.formAddress,
                onValueChange = { vm.updateFormAddress(it) },
                placeholder = {
                    Text("192.168.x.x", fontFamily = FontFamily.Monospace, color = IndustrialTextDim, fontSize = 13.sp)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RectangleShape,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = IndustrialText),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = industrialTextFieldColors(),
            )
        }

        // Port (LTR, narrow)
        FieldLabel("יציאה TCP")
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = state.formPort,
                onValueChange = { vm.updateFormPort(it) },
                placeholder = {
                    Text("9100", fontFamily = FontFamily.Monospace, color = IndustrialTextDim, fontSize = 13.sp)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RectangleShape,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = IndustrialText),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = industrialTextFieldColors(),
            )
        }

        // Name
        FieldLabel("שם מדפסת")
        OutlinedTextField(
            value = state.formName,
            onValueChange = { vm.updateFormName(it) },
            placeholder = {
                Text("מדפסת מטבח", fontFamily = FontFamily.Monospace, color = IndustrialTextDim, fontSize = 13.sp)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RectangleShape,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = IndustrialText),
            colors = industrialTextFieldColors(),
        )

        // Notes
        FieldLabel("הערות (אופציונלי — דגם, מיקום)")
        OutlinedTextField(
            value = state.formNotes,
            onValueChange = { vm.updateFormNotes(it) },
            placeholder = {
                Text("SNBC BTP-R880NPII", fontFamily = FontFamily.Monospace, color = IndustrialTextDim, fontSize = 12.sp)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RectangleShape,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = IndustrialText),
            colors = industrialTextFieldColors(),
        )

        HorizontalDivider(color = IndustrialBorder, modifier = Modifier.padding(vertical = 2.dp))

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.testFormConnection() },
                enabled = !state.isFormChecking,
                shape = RectangleShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = IndustrialText),
                border = androidx.compose.foundation.BorderStroke(1.dp, IndustrialBorder),
                modifier = Modifier.weight(1f),
            ) {
                if (state.isFormChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = IndustrialTextDim)
                    Spacer(Modifier.width(4.dp))
                }
                Text("בדוק TCP", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }

            Button(
                onClick = { vm.savePrinter() },
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = IndustrialBlue, contentColor = Color.White),
                modifier = Modifier.weight(1f),
            ) {
                Text("שמור", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            if (state.editingId != null) {
                TextButton(onClick = { vm.clearForm() }) {
                    Text("בטל", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = IndustrialTextDim)
                }
            }
        }

        // TCP check result
        state.formCheckMessage?.let { msg ->
            val isOk = msg.startsWith("✓")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IndustrialSurface)
                    .border(1.dp, if (isOk) IndustrialGreen.copy(alpha = 0.4f) else IndustrialRed.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    "TCP",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = IndustrialTextDim,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    msg,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (isOk) IndustrialGreen else IndustrialRed,
                )
            }
        }

        // Validation error
        state.formError?.let { err ->
            MonoText("⚠ $err", IndustrialRed, fontSize = 12.sp)
        }
    }
}

// ── Printer card ──────────────────────────────────────────────────────────────

@Composable
private fun PrinterCard(
    printer: PrinterRecord,
    isChecking: Boolean,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetPrimary: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IndustrialSurface)
            .border(
                1.dp,
                if (printer.isPrimary) IndustrialBlue.copy(alpha = 0.6f) else IndustrialBorder,
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Name row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (printer.isPrimary) {
                Text(
                    "★",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = IndustrialBlue,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                printer.name,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = IndustrialText,
                modifier = Modifier.weight(1f),
            )
            if (printer.isPrimary) {
                Text(
                    "ראשית",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = IndustrialBlue,
                    modifier = Modifier
                        .background(IndustrialBlue.copy(alpha = 0.12f))
                        .border(1.dp, IndustrialBlue.copy(alpha = 0.3f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }

        // Address
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                "${printer.address} : ${printer.port}",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = IndustrialTextDim,
            )
        }

        // Notes (if any)
        if (printer.notes.isNotBlank()) {
            MonoText(printer.notes, IndustrialTextDim, fontSize = 11.sp)
        }

        // Last check status
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoText("בדיקה אחרונה: ", IndustrialTextDim, fontSize = 11.sp)
            if (isChecking) {
                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp, color = IndustrialAmber)
                Spacer(Modifier.width(4.dp))
                MonoText("בודק…", IndustrialAmber, fontSize = 11.sp)
            } else if (printer.lastCheckMs == 0L) {
                MonoText("—", IndustrialTextDim, fontSize = 11.sp)
            } else {
                val symbol = if (printer.lastCheckOk) "✓" else "✗"
                val label  = if (printer.lastCheckOk) "נגיש" else "לא נגיש"
                val color  = if (printer.lastCheckOk) IndustrialGreen else IndustrialRed
                MonoText("$symbol $label · ${formatCheckTime(printer.lastCheckMs)}", color, fontSize = 11.sp)
            }
        }

        HorizontalDivider(color = IndustrialBorder, modifier = Modifier.padding(vertical = 2.dp))

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onTest, enabled = !isChecking) {
                Text("[ בדוק ]", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialBlue)
            }
            TextButton(onClick = onEdit) {
                Text("[ ערוך ]", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialTextDim)
            }
            if (!printer.isPrimary) {
                TextButton(onClick = onSetPrimary) {
                    Text("[ ★ ראשית ]", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialTextDim)
                }
            }
            Spacer(Modifier.weight(1f))
            if (confirmDelete) {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("[ ביטול ]", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialTextDim)
                }
                TextButton(onClick = { onDelete(); confirmDelete = false }) {
                    Text("[ אשר מחיקה ]", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialRed)
                }
            } else {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("[ מחק ]", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialRed)
                }
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun StatusBanner(msg: StatusMessage, onDismiss: () -> Unit) {
    val color = if (msg.isError) IndustrialRed else IndustrialGreen
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            msg.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = color,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onDismiss,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
        ) {
            Text("[ סגור ]", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = IndustrialTextDim)
        }
    }
}

@Composable
private fun PrinterSectionHeader(number: String, title: String) {
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
            Text(number, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IndustrialText)
    }
}

@Composable
private fun FieldLabel(label: String) {
    Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialTextDim)
}

@Composable
private fun MonoText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
) {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = fontSize, color = color)
}

@Composable
private fun industrialTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = IndustrialText,
    unfocusedTextColor = IndustrialText,
    focusedContainerColor = IndustrialSurface,
    unfocusedContainerColor = IndustrialSurface,
    focusedBorderColor = IndustrialBorderActive,
    unfocusedBorderColor = IndustrialBorder,
    cursorColor = IndustrialBlue,
    focusedPlaceholderColor = IndustrialTextDim,
    unfocusedPlaceholderColor = IndustrialTextDim,
)
