package com.commu.printeragent.ui.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.commu.printeragent.agent.AgentStatus
import com.commu.printeragent.agent.JobResult
import com.commu.printeragent.printing.formatCheckTime
import com.commu.printeragent.ui.theme.IndustrialAmber
import com.commu.printeragent.ui.theme.IndustrialBorder
import com.commu.printeragent.ui.theme.IndustrialBlue
import com.commu.printeragent.ui.theme.IndustrialDark
import com.commu.printeragent.ui.theme.IndustrialGreen
import com.commu.printeragent.ui.theme.IndustrialPanel
import com.commu.printeragent.ui.theme.IndustrialRed
import com.commu.printeragent.ui.theme.IndustrialSurface
import com.commu.printeragent.ui.theme.IndustrialText
import com.commu.printeragent.ui.theme.IndustrialTextDim

@Composable
fun JobQueueScreen(
    modifier: Modifier = Modifier,
    vm: JobQueueViewModel = viewModel(),
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
            // ── Title bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IndustrialPanel)
                    .border(1.dp, IndustrialBorder)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonoText("COMMU PRINTER AGENT  v1.0", IndustrialTextDim, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                MonoText("● תורים", IndustrialAmber, fontSize = 11.sp)
            }

            Spacer(Modifier.height(12.dp))

            // ── Section [3]: Automatic Agent ──────────────────────────────────
            JobSectionHeader(number = "3", title = "סוכן הדפסה אוטומטי")
            Spacer(Modifier.height(8.dp))

            // Start / Stop button
            val agentRunning = state.agentStatus !is AgentStatus.Stopped
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (agentRunning) {
                    Button(
                        onClick = { vm.stopAgent() },
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IndustrialRed,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("■ עצור סוכן", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = { vm.startAgent() },
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IndustrialGreen,
                            contentColor = IndustrialDark,
                        ),
                    ) {
                        Text("▶ הפעל סוכן", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Agent status indicator
            val agentStatusColor = when (state.agentStatus) {
                is AgentStatus.Polling  -> IndustrialGreen
                is AgentStatus.Idle     -> IndustrialBlue
                is AgentStatus.Starting -> IndustrialAmber
                is AgentStatus.BackingOff, is AgentStatus.NoConfig -> IndustrialAmber
                is AgentStatus.Stopped  -> IndustrialTextDim
            }
            val agentStatusText = when (state.agentStatus) {
                is AgentStatus.Polling   -> "בודק תור הדפסה…"
                is AgentStatus.Idle      -> "ממתין לעבודות חדשות"
                is AgentStatus.Starting  -> "מתחיל…"
                is AgentStatus.BackingOff -> "שגיאת חיבור — ניסיון חוזר בקרוב"
                is AgentStatus.NoConfig  -> "הגדרות חסרות — בדוק חיבור ומדפסות"
                is AgentStatus.Stopped   -> "עצור"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IndustrialSurface)
                    .border(1.dp, agentStatusColor.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonoText("● $agentStatusText", agentStatusColor)
            }

            if (state.agentLastPollMs > 0L || state.agentSessionPrinted > 0 || state.agentSessionFailed > 0) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (state.agentLastPollMs > 0L) {
                        MonoText("בדיקה: ${formatCheckTime(state.agentLastPollMs)}", IndustrialTextDim)
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    if (state.agentSessionPrinted > 0 || state.agentSessionFailed > 0) {
                        MonoText(
                            "הדפסות: ${state.agentSessionPrinted}  שגיאות: ${state.agentSessionFailed}",
                            IndustrialTextDim,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Section [4]: Manual Queue ──────────────────────────────────────
            JobSectionHeader(number = "4", title = "תור הדפסה ידני")
            Spacer(Modifier.height(8.dp))

            // Action row: button + last-run time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { vm.processJobQueue() },
                    enabled = !state.isProcessing,
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IndustrialBlue,
                        contentColor = Color.White,
                        disabledContainerColor = IndustrialBorder,
                    ),
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("בדוק ועבד תור הדפסה", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                if (state.lastRunMs > 0L) {
                    MonoText("ריצה אחרונה: ${formatCheckTime(state.lastRunMs)}", IndustrialTextDim)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Status / progress line
            state.statusLine?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IndustrialSurface)
                        .border(1.dp, IndustrialAmber.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    MonoText("⟳  $msg", IndustrialAmber)
                }
                Spacer(Modifier.height(6.dp))
            }

            // Error message
            state.errorMessage?.let { err ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IndustrialSurface)
                        .border(1.dp, IndustrialRed.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonoText("⚠  $err", IndustrialRed, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { vm.clearResults() },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                    ) {
                        Text("×", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = IndustrialTextDim)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // Results section
            if (state.jobResults.isNotEmpty()) {
                HorizontalDivider(color = IndustrialBorder)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonoText(
                        "${state.jobResults.size} עובד · ${state.printedCount} הודפס · ${state.failedCount} נכשל",
                        IndustrialTextDim,
                    )
                    TextButton(
                        onClick = { vm.clearResults() },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                    ) {
                        Text("[ נקה ]", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = IndustrialTextDim)
                    }
                }

                Spacer(Modifier.height(6.dp))

                state.jobResults.forEach { result ->
                    JobResultRow(result = result)
                    Spacer(Modifier.height(4.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Job result row ────────────────────────────────────────────────────────────

@Composable
private fun JobResultRow(result: JobResult) {
    val borderColor = if (result.isSuccess) IndustrialGreen.copy(alpha = 0.4f) else IndustrialRed.copy(alpha = 0.4f)
    val iconColor   = if (result.isSuccess) IndustrialGreen else IndustrialRed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IndustrialSurface)
            .border(1.dp, borderColor)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (result.isSuccess) "✓" else "✗",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = iconColor,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "עבודה #${result.jobId} · ${result.printerName}",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = IndustrialText,
            )
            Text(
                result.message,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (result.isSuccess) IndustrialGreen else IndustrialRed,
            )
        }
    }
}

// ── Shared composable helpers ─────────────────────────────────────────────────

@Composable
private fun JobSectionHeader(number: String, title: String) {
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
                number,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = IndustrialText,
        )
    }
}

@Composable
private fun MonoText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    modifier: Modifier = Modifier,
) {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = fontSize, color = color, modifier = modifier)
}
