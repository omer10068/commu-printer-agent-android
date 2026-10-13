package com.commu.printeragent.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.commu.printeragent.agent.AgentStatus
import com.commu.printeragent.data.PrinterRecord
import com.commu.printeragent.data.TestPrintStatus
import com.commu.printeragent.ui.onboarding.step4.PrintRowStatus
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(modifier: Modifier = Modifier, vm: DashboardViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()

    // Auto-dismiss test toast
    LaunchedEffect(state.testToastMessage) {
        if (state.testToastMessage != null) {
            delay(2000)
            vm.clearTestToast()
        }
    }

    // Physical test confirmation dialog
    val testDialogId = state.testDialogPrinterId
    if (testDialogId != null) {
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
                TextButton(onClick = { vm.onPhysicalTestConfirm(testDialogId, true) }) {
                    Text("כן", fontFamily = FontFamily.Monospace, color = IndustrialGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.onPhysicalTestConfirm(testDialogId, false) }) {
                    Text("לא", fontFamily = FontFamily.Monospace, color = IndustrialRed)
                }
            },
            containerColor = IndustrialPanel,
            titleContentColor = IndustrialText,
            textContentColor = IndustrialTextDim,
        )
    }

    // Delete confirmation dialog
    val deleteConfirmId = state.deleteConfirmPrinterId
    if (deleteConfirmId != null) {
        val printerName = state.savedPrinters.find { it.id == deleteConfirmId }?.name ?: deleteConfirmId
        AlertDialog(
            onDismissRequest = { vm.cancelDeletePrinter() },
            title = {
                Text(
                    text = "הסרת מדפסת",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = IndustrialText,
                )
            },
            text = {
                Text(
                    text = "האם להסיר את $printerName מהרשימה המקומית?",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = IndustrialTextDim,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmDeletePrinter() }) {
                    Text("הסר", fontFamily = FontFamily.Monospace, color = IndustrialRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelDeletePrinter() }) {
                    Text("ביטול", fontFamily = FontFamily.Monospace, color = IndustrialTextDim)
                }
            },
            containerColor = IndustrialPanel,
            titleContentColor = IndustrialText,
            textContentColor = IndustrialTextDim,
        )
    }

    // Save sheet
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (state.showSaveSheet) {
        ModalBottomSheet(
            onDismissRequest = { vm.hideSaveSheet() },
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
                            text = state.saveSheetIp,
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
                        value = state.saveSheetName,
                        onValueChange = { vm.updateSaveSheetName(it) },
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
                        colors = industrialFieldColors(),
                    )
                    if (state.saveSheetError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.saveSheetError!!,
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
                            onClick = { vm.hideSaveSheet() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("ביטול", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = IndustrialTextDim)
                        }
                        Button(
                            onClick = { vm.saveScanResult() },
                            modifier = Modifier.weight(2f),
                            shape = RectangleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IndustrialBlue,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text("שמור", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = modifier.background(IndustrialDark)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Status card (always visible)
                StatusCard(
                    businessName = state.businessName,
                    printerCount = state.printerCount,
                    agentStatus = state.agentStatus,
                    sessionPrinted = state.agentSessionPrinted,
                    sessionFailed = state.agentSessionFailed,
                    onStart = { vm.startAgent() },
                    onStop = { vm.stopAgent() },
                )

                // Scrollable accordion sections
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Section 1 — פרטי חיבור
                    AccordionSection(
                        number = "1",
                        title = "פרטי חיבור",
                        expanded = state.section1Expanded,
                        onToggle = { vm.toggleSection1() },
                    ) {
                        ConnectionEditContent(state = state, vm = vm)
                    }

                    // Section 2 — מדפסות
                    AccordionSection(
                        number = "2",
                        title = "מדפסות",
                        expanded = state.section2Expanded,
                        onToggle = { vm.toggleSection2() },
                    ) {
                        PrintersContent(state = state, vm = vm)
                    }

                    // Section 3 — הדפסות בדיקה
                    AccordionSection(
                        number = "3",
                        title = "הדפסות בדיקה",
                        expanded = state.section3Expanded,
                        onToggle = { vm.toggleSection3() },
                    ) {
                        TestPrintSectionContent(state = state, vm = vm)
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }

            // Toast overlay
            if (state.testToastMessage != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .background(IndustrialPanel)
                        .border(1.dp, IndustrialBorder)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = state.testToastMessage!!,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = IndustrialText,
                    )
                }
            }
        }
    }
}

// ── Status card ───────────────────────────────────────────────────────────────

@Composable
private fun StatusCard(
    businessName: String,
    printerCount: Int,
    agentStatus: AgentStatus,
    sessionPrinted: Int,
    sessionFailed: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val isActive = agentStatus !is AgentStatus.Stopped && agentStatus !is AgentStatus.NoConfig
    val statusDot = if (isActive) "●" else "○"
    val statusColor = when {
        isActive -> IndustrialGreen
        agentStatus is AgentStatus.BackingOff -> IndustrialAmber
        else -> IndustrialRed
    }
    val statusText = when (agentStatus) {
        AgentStatus.Stopped  -> "מופסק"
        AgentStatus.Starting -> "מתחיל…"
        AgentStatus.Polling  -> "מסנכרן…"
        is AgentStatus.Idle  -> "פעיל"
        is AgentStatus.BackingOff -> "מחכה — ${agentStatus.reason}"
        AgentStatus.NoConfig -> "הגדרות חסרות"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IndustrialSurface)
            .border(1.dp, IndustrialBorder)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "COMMU PRINTER AGENT",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = IndustrialTextDim,
                )
                if (businessName.isNotBlank()) {
                    Text(
                        text = businessName,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndustrialText,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = statusDot,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = statusColor,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = statusText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = statusColor,
                    )
                    if (printerCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "· $printerCount מדפסות",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = IndustrialTextDim,
                        )
                    }
                }
                if (sessionPrinted > 0 || sessionFailed > 0) {
                    Text(
                        text = "סשן: $sessionPrinted הודפסו · $sessionFailed נכשלו",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = IndustrialTextDim,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (!isActive) {
                Button(
                    onClick = onStart,
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IndustrialGreen,
                        contentColor = Color.Black,
                    ),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("הפעל", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onStop,
                    shape = RectangleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = IndustrialRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndustrialRed.copy(alpha = 0.6f)),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("עצור", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Accordion ─────────────────────────────────────────────────────────────────

@Composable
private fun AccordionSection(
    number: String,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(IndustrialPanel)
                .border(1.dp, IndustrialBorder)
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(IndustrialBlue)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = number,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = IndustrialText,
                )
            }
            Text(
                text = if (expanded) "▲" else "▼",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = IndustrialTextDim,
            )
        }
        AnimatedVisibility(visible = expanded) {
            content()
        }
    }
}

// ── Section 1 — Connection edit ───────────────────────────────────────────────

@Composable
private fun ConnectionEditContent(state: DashboardUiState, vm: DashboardViewModel) {
    var urlInput by remember(state.connectionUrl) { mutableStateOf(state.connectionUrl) }
    var tenantInput by remember(state.connectionTenantId) { mutableStateOf(state.connectionTenantId) }
    var tokenInput by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IndustrialDark)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DashFieldLabel("כתובת המערכת")
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it; vm.updateConnectionUrl(it) },
                placeholder = { Text("http://192.168.x.x:5100", fontFamily = FontFamily.Monospace, color = IndustrialTextDim, fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RectangleShape,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = IndustrialText),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = industrialFieldColors(),
            )
        }

        DashFieldLabel("מזהה דייר (Tenant ID)")
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = tenantInput,
                onValueChange = { tenantInput = it; vm.updateConnectionTenantId(it) },
                placeholder = { Text("demo", fontFamily = FontFamily.Monospace, color = IndustrialTextDim, fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RectangleShape,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = IndustrialText),
                colors = industrialFieldColors(),
            )
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            DashFieldLabel("מפתח זיהוי (Token)")
            TextButton(
                onClick = { showToken = !showToken },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Text(if (showToken) "[ הסתר ]" else "[ הצג ]", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialBlue)
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it; vm.updateConnectionToken(it) },
                placeholder = {
                    Text(
                        if (state.connectionHasToken) "●●●●●● (אסימון שמור)" else "pa_...",
                        fontFamily = FontFamily.Monospace,
                        color = IndustrialTextDim,
                        fontSize = 12.sp,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RectangleShape,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = IndustrialText),
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = industrialFieldColors(),
            )
        }

        HorizontalDivider(color = IndustrialBorder)

        Button(
            onClick = { vm.saveConnection(urlInput, tenantInput, tokenInput) },
            enabled = state.connectionChanged && !state.connectionSaving,
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = IndustrialBlue,
                contentColor = Color.White,
                disabledContainerColor = IndustrialSurface,
                disabledContentColor = IndustrialTextDim,
            ),
        ) {
            if (state.connectionSaving) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(6.dp))
            }
            Text("שמור שינויים", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        if (state.connectionSaveStatus != null) {
            val isSuccess = state.connectionSaveStatus!!.startsWith("✓")
            Text(
                text = state.connectionSaveStatus!!,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (isSuccess) IndustrialGreen else IndustrialRed,
            )
        }
    }
}

// ── Section 2 — Printers ──────────────────────────────────────────────────────

@Composable
private fun PrintersContent(state: DashboardUiState, vm: DashboardViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IndustrialDark)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Saved printers
        if (state.savedPrinters.isNotEmpty()) {
            Text(
                text = "מדפסות שמורות (${state.savedPrinters.size}):",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = IndustrialTextDim,
            )
            state.savedPrinters.forEach { printer ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IndustrialSurface)
                        .border(1.dp, IndustrialBorder)
                        .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(printer.name, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = IndustrialText)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text("${printer.address}:${printer.port}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialTextDim)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (printer.isPrimary) {
                            Box(
                                modifier = Modifier
                                    .background(IndustrialBlue.copy(alpha = 0.15f))
                                    .border(1.dp, IndustrialBlue.copy(alpha = 0.4f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text("ראשית", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = IndustrialBlue)
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        TextButton(
                            onClick = { vm.requestDeletePrinter(printer.id) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("הסר", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialRed)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Scan button
        Button(
            onClick = { vm.startScan() },
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
            if (state.isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(6.dp))
            }
            Text("חפש מדפסות נוספות", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        // Scan progress
        if (state.isScanning && state.scanTotal > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "סורק · ${state.scanProgress}/${state.scanTotal}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = IndustrialTextDim,
                )
            }
            LinearProgressIndicator(
                progress = { state.scanProgress.toFloat() / state.scanTotal.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = IndustrialBlue,
                trackColor = IndustrialSurface,
            )
        }

        // Scan candidates
        if (state.scanCandidates.isNotEmpty()) {
            Text(
                text = "נמצאו ${state.scanCandidates.size} מועמדים:",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = IndustrialTextDim,
            )
            state.scanCandidates.forEach { ip ->
                val alreadySaved = state.savedPrinters.any { it.address == ip }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IndustrialSurface)
                        .border(1.dp, IndustrialBorder)
                        .clickable(enabled = !alreadySaved) { vm.showSaveSheet(ip) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(ip, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = IndustrialText)
                    }
                    if (alreadySaved) {
                        Text("שמורה", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialGreen)
                    } else {
                        Text("⊕", fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = IndustrialBlue)
                    }
                }
            }
        }

        // Sync status
        if (state.isSyncing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = IndustrialBlue)
                Spacer(Modifier.width(6.dp))
                Text("מסנכרן…", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialTextDim)
            }
        } else if (state.syncMessage != null) {
            val isSuccess = state.syncMessage!!.startsWith("✓")
            Text(
                text = state.syncMessage!!,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (isSuccess) IndustrialGreen else IndustrialRed,
            )
        }
    }
}

// ── Section 3 — Test print ────────────────────────────────────────────────────

@Composable
private fun TestPrintSectionContent(state: DashboardUiState, vm: DashboardViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IndustrialDark)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.savedPrinters.isEmpty()) {
            Text(
                text = "אין מדפסות שמורות",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = IndustrialAmber,
            )
        } else {
            Text(
                text = "לחץ על מדפסת כדי להדפיס דף בדיקה:",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = IndustrialTextDim,
            )
            state.savedPrinters.forEach { printer ->
                val rowStatus = state.testRowStatus[printer.id] ?: PrintRowStatus.Idle
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
                        .clickable(enabled = !isPrinting) { vm.sendTestPrint(printer) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(printer.name, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IndustrialText)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text("${printer.address}:${printer.port}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialTextDim)
                        }
                    }
                    when {
                        isPrinting -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = IndustrialBlue)
                                Spacer(Modifier.width(6.dp))
                                Text("מדפיס…", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialBlue)
                            }
                        }
                        printer.testPrintStatus == TestPrintStatus.Verified ->
                            Text("תקינה ✓", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialGreen)
                        printer.testPrintStatus == TestPrintStatus.Failed ->
                            Text("לא הודפס", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialRed)
                        rowStatus == PrintRowStatus.TcpFailed ->
                            Text("לא הגיבה", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialAmber)
                        else ->
                            Text("לא נבדקה", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialTextDim)
                    }
                }
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun DashFieldLabel(label: String) {
    Text(
        text = label,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = IndustrialTextDim,
    )
}

@Composable
private fun industrialFieldColors() = OutlinedTextFieldDefaults.colors(
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
