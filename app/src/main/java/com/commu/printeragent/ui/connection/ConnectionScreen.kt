package com.commu.printeragent.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
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

// Industrial control-panel colors
private val BgDark = Color(0xFF0D1117)
private val Surface1 = Color(0xFF161B22)
private val Surface2 = Color(0xFF21262D)
private val BorderNormal = Color(0xFF30363D)
private val BorderActive = Color(0xFF388BFD)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextDim = Color(0xFF8B949E)
private val AccentBlue = Color(0xFF1F6FEB)
private val StatusGreen = Color(0xFF3FB950)
private val StatusRed = Color(0xFFF85149)
private val StatusAmber = Color(0xFFD29922)

@Composable
fun ConnectionScreen(
    modifier: Modifier = Modifier,
    vm: ConnectionViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()

    var urlInput by remember { mutableStateOf("") }
    var tenantInput by remember { mutableStateOf("") }
    var tokenInput by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }

    // Seed fields once when persisted settings are loaded from DataStore
    LaunchedEffect(state.isSettingsLoaded) {
        if (state.isSettingsLoaded) {
            urlInput = state.backendUrl
            tenantInput = state.tenantId
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier
                .background(BgDark)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // App title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface2)
                    .border(1.dp, BorderNormal)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "COMMU PRINTER AGENT  v1.0",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextDim,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "● הגדרות",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = AccentBlue,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Keystore error banner — shown when EncryptedSharedPreferences cannot be opened
            if (state.keystoreError) {
                KeystoreErrorBanner(onReset = { vm.resetCredentials() })
                Spacer(Modifier.height(8.dp))
            }

            // Section [1] header
            SectionHeader(number = "1", title = "חיבור לשרת")

            Spacer(Modifier.height(8.dp))

            // Backend URL
            FieldLabel("כתובת שרת (Backend URL)")
            Spacer(Modifier.height(4.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it; vm.clearTestResult() },
                    placeholder = {
                        Text(
                            "http://192.168.x.x:5100",
                            fontFamily = FontFamily.Monospace,
                            color = TextDim,
                            fontSize = 13.sp,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RectangleShape,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = TextPrimary,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = industrialTextFieldColors(),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Tenant ID
            FieldLabel("מזהה דייר (Tenant ID)")
            Spacer(Modifier.height(4.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = tenantInput,
                    onValueChange = { tenantInput = it; vm.clearTestResult() },
                    placeholder = {
                        Text(
                            "demo",
                            fontFamily = FontFamily.Monospace,
                            color = TextDim,
                            fontSize = 13.sp,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RectangleShape,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = TextPrimary,
                    ),
                    colors = industrialTextFieldColors(),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Agent Token
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                FieldLabel("אסימון סוכן (Agent Token)")
                TextButton(
                    onClick = { showToken = !showToken },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(
                        if (showToken) "[ הסתר ]" else "[ הצג ]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = AccentBlue,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it; vm.clearTestResult() },
                    placeholder = {
                        Text(
                            if (state.hasStoredToken) "●●●●●● (אסימון שמור — השאר ריק לשימוש בשמור)"
                            else "pa_...",
                            fontFamily = FontFamily.Monospace,
                            color = TextDim,
                            fontSize = 12.sp,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RectangleShape,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = TextPrimary,
                    ),
                    visualTransformation = if (showToken) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = industrialTextFieldColors(),
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = BorderNormal)
            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { vm.testAndSave(urlInput, tenantInput, tokenInput) },
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue,
                        contentColor = Color.White,
                        disabledContainerColor = Surface2,
                        disabledContentColor = TextDim,
                    ),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        "בדוק חיבור",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }

                OutlinedButton(
                    onClick = { vm.saveWithoutTest(urlInput, tenantInput, tokenInput) },
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f),
                    shape = RectangleShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary,
                        disabledContentColor = TextDim,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderNormal),
                ) {
                    Text(
                        "שמור הגדרות",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Status area
            StatusPanel(state)

            Spacer(Modifier.height(8.dp))

            // Hint text
            Text(
                text = "\"בדוק חיבור\" — בודק חיבור API ושומר בהצלחה  |  \"שמור\" — שומר ללא בדיקה",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TextDim,
            )
        }
    }
}

@Composable
private fun SectionHeader(number: String, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface1)
            .border(1.dp, BorderNormal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(AccentBlue)
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
            color = TextPrimary,
        )
    }
}

@Composable
private fun FieldLabel(label: String) {
    Text(
        text = label,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = TextDim,
    )
}

@Composable
private fun StatusPanel(state: ConnectionUiState) {
    val (indicator, text, color) = when (val result = state.testResult) {
        is TestResult.Idle -> if (state.isSaved)
            Triple("●", "ההגדרות נשמרו", StatusGreen)
        else
            Triple("○", "ממתין לבדיקה", TextDim)

        is TestResult.Success -> if (state.isSaved)
            Triple("●", "חיבור הצליח — ההגדרות נשמרו", StatusGreen)
        else
            Triple("⚠", "חיבור הצליח — שגיאה בשמירה, נסה שנית", StatusAmber)

        is TestResult.Failure ->
            Triple("✗", result.message, StatusRed)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface1)
            .border(1.dp, when (color) {
                StatusRed -> StatusRed.copy(alpha = 0.4f)
                StatusAmber -> StatusAmber.copy(alpha = 0.4f)
                else -> BorderNormal
            })
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "STATUS",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = TextDim,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = indicator,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = color,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = color,
        )
    }
}

@Composable
private fun KeystoreErrorBanner(onReset: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StatusRed.copy(alpha = 0.12f))
            .border(1.dp, StatusRed.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "⚠ שגיאת מאגר מפתחות — לא ניתן לגשת לאסימון שמור",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = StatusRed,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onReset) {
            Text(
                "[ איפוס ]",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = StatusAmber,
            )
        }
    }
}

@Composable
private fun industrialTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = Surface1,
    unfocusedContainerColor = Surface1,
    focusedBorderColor = BorderActive,
    unfocusedBorderColor = BorderNormal,
    cursorColor = AccentBlue,
    focusedPlaceholderColor = TextDim,
    unfocusedPlaceholderColor = TextDim,
)
