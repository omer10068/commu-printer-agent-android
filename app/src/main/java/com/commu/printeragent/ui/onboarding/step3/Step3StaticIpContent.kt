package com.commu.printeragent.ui.onboarding.step3

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.commu.printeragent.data.IpConfigStatus
import com.commu.printeragent.data.PrinterRecord
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

@Composable
fun Step3StaticIpContent(vm: Step3ViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val pendingUrl by vm.pendingBrowserUrl.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Open browser when a URL is pending
    LaunchedEffect(pendingUrl) {
        val url = pendingUrl ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        vm.onBrowserUrlConsumed()
    }

    // Detect when we return from the browser
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onResumedFromBrowser()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Delete confirmation dialog
    val deleteConfirmId = state.deleteConfirmPrinterId
    if (deleteConfirmId != null) {
        val printerName = state.printers.find { it.id == deleteConfirmId }?.name ?: deleteConfirmId
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

    // IP config confirmation dialog
    val dialogPrinterId = state.showingDialogForPrinterId
    if (dialogPrinterId != null) {
        AlertDialog(
            onDismissRequest = { /* user must choose */ },
            title = {
                Text(
                    text = "קיבעת את כתובת ה-IP בהצלחה?",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = IndustrialText,
                )
            },
            text = {
                Text(
                    text = "האם הגדרת כתובת IP קבועה בעמוד ההגדרות של המדפסת?",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = IndustrialTextDim,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.onIpConfigConfirmed(dialogPrinterId, true) }) {
                    Text(
                        "כן",
                        fontFamily = FontFamily.Monospace,
                        color = IndustrialGreen,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.onIpConfigConfirmed(dialogPrinterId, false) }) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IndustrialDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Section header
        Step3SectionHeader()

        Spacer(Modifier.height(6.dp))

        Text(
            text = "לחץ על מדפסת כדי לפתוח את ממשק ההגדרות שלה בדפדפן.",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = IndustrialTextDim,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "שם תוכל להגדיר כתובת IP קבועה (Static IP) כדי שהמדפסת לא תשנה כתובת.",
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
                PrinterIpRow(
                    printer = printer,
                    onClick = { vm.openBrowserForPrinter(printer) },
                    onDelete = { vm.requestDeletePrinter(printer.id) },
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "הערה: אם דגם המדפסת שלך אינו תומך בהגדרת IP קבוע דרך ממשק הרשת, תעד זאת ופנה לתמיכה הטכנית.",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = IndustrialTextDim,
        )
    }
}

@Composable
private fun PrinterIpRow(printer: PrinterRecord, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IndustrialSurface)
            .border(
                1.dp,
                when (printer.ipConfigStatus) {
                    IpConfigStatus.Configured -> IndustrialGreen.copy(alpha = 0.4f)
                    IpConfigStatus.Failed     -> IndustrialRed.copy(alpha = 0.4f)
                    IpConfigStatus.Pending    -> IndustrialBorder
                }
            )
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            IpConfigStatusTag(printer.ipConfigStatus)
            Spacer(Modifier.width(4.dp))
            Text(
                text = "→",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = IndustrialBlue,
            )
            Spacer(Modifier.width(4.dp))
            // Nested clickable — consumes pointer events so the outer row click is not triggered
            TextButton(
                onClick = onDelete,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("הסר", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = IndustrialRed)
            }
        }
    }
}

@Composable
private fun IpConfigStatusTag(status: IpConfigStatus) {
    val (text, color) = when (status) {
        IpConfigStatus.Pending    -> Pair("לא קובע עדיין", IndustrialTextDim)
        IpConfigStatus.Configured -> Pair("קובע ✓", IndustrialGreen)
        IpConfigStatus.Failed     -> Pair("לא קובע", IndustrialRed)
    }
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = color,
    )
}

@Composable
private fun Step3SectionHeader() {
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
                text = "3",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "קיבוע כתובת IP",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = IndustrialText,
        )
    }
}
