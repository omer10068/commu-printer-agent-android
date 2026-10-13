package com.commu.printeragent.ui.onboarding.step1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
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
import com.commu.printeragent.ui.connection.ConnectionViewModel
import com.commu.printeragent.ui.connection.TestResult
import com.commu.printeragent.ui.onboarding.WizardViewModel
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
fun Step1ConnectionContent(
    wizardVm: WizardViewModel,
    vm: ConnectionViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()

    var urlInput by remember { mutableStateOf("") }
    var tenantInput by remember { mutableStateOf("") }
    var tokenInput by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current

    // Seed fields once when persisted settings are loaded
    LaunchedEffect(state.isSettingsLoaded) {
        if (state.isSettingsLoaded) {
            urlInput = state.backendUrl
            tenantInput = state.tenantId
        }
    }

    // When connection succeeds, mark step 1 complete
    LaunchedEffect(state.testResult) {
        if (state.testResult is TestResult.Success) {
            wizardVm.markStep1Complete()
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
        WizardSectionHeader(number = "1", title = "חיבור לשרת")

        Spacer(Modifier.height(6.dp))

        Text(
            text = "את הפרטים מעתיקים מממשק הניהול של המערכת.",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = IndustrialTextDim,
        )

        Spacer(Modifier.height(12.dp))

        // Keystore error banner
        if (state.keystoreError) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IndustrialRed.copy(alpha = 0.12f))
                    .border(1.dp, IndustrialRed.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "⚠ שגיאת מאגר מפתחות — לא ניתן לגשת לאסימון שמור",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = IndustrialRed,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // Success card
        if (state.testResult is TestResult.Success && state.isSaved) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IndustrialGreen.copy(alpha = 0.12f))
                    .border(1.dp, IndustrialGreen.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "● מחובר בהצלחה · ${state.tenantId}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = IndustrialGreen,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { vm.clearTestResult() },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(
                        "[ ערוך ]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = IndustrialBlue,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // URL field
        WizardFieldLabel("כתובת המערכת")
        Spacer(Modifier.height(4.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it; vm.clearTestResult() },
                placeholder = {
                    Text(
                        "http://192.168.x.x:5100",
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = industrialFieldColors(),
            )
        }

        Spacer(Modifier.height(10.dp))

        // Tenant ID field
        WizardFieldLabel("מזהה דייר (Tenant ID)")
        Spacer(Modifier.height(4.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = tenantInput,
                onValueChange = { tenantInput = it; vm.clearTestResult() },
                placeholder = {
                    Text(
                        "demo",
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
        }

        Spacer(Modifier.height(10.dp))

        // Token field with paste button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            WizardFieldLabel("מפתח זיהוי (Agent Token)")
            Row {
                TextButton(
                    onClick = {
                        val pasted = clipboardManager.getText()?.text
                        if (!pasted.isNullOrBlank()) {
                            tokenInput = pasted.trim()
                            vm.clearTestResult()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(
                        "[ הדבק ]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = IndustrialBlue,
                    )
                }
                TextButton(
                    onClick = { showToken = !showToken },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(
                        if (showToken) "[ הסתר ]" else "[ הצג ]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = IndustrialBlue,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it; vm.clearTestResult() },
                placeholder = {
                    Text(
                        if (state.hasStoredToken) "●●●●●● (אסימון שמור)" else "pa_...",
                        fontFamily = FontFamily.Monospace,
                        color = IndustrialTextDim,
                        fontSize = 12.sp,
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
                visualTransformation = if (showToken) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = industrialFieldColors(),
            )
        }

        Spacer(Modifier.height(16.dp))

        // Connect button
        Button(
            onClick = { vm.testAndSave(urlInput, tenantInput, tokenInput) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = IndustrialBlue,
                contentColor = Color.White,
                disabledContainerColor = IndustrialSurface,
                disabledContentColor = IndustrialTextDim,
            ),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "מתחבר למערכת…",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            } else {
                Text(
                    "התחבר",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }

        // Error message
        if (state.testResult is TestResult.Failure) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "החיבור נכשל — בדוק את כתובת המערכת ומפתח הזיהוי",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = IndustrialRed,
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WizardSectionHeader(number: String, title: String) {
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
                text = number,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
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
}

@Composable
private fun WizardFieldLabel(label: String) {
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
