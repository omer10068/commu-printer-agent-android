package com.commu.printeragent.ui.onboarding

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.commu.printeragent.ui.onboarding.step1.Step1ConnectionContent
import com.commu.printeragent.ui.onboarding.step2.Step2DiscoveryContent
import com.commu.printeragent.ui.onboarding.step3.Step3StaticIpContent
import com.commu.printeragent.ui.onboarding.step4.Step4TestPrintContent
import com.commu.printeragent.ui.theme.IndustrialBlue
import com.commu.printeragent.ui.theme.IndustrialBorder
import com.commu.printeragent.ui.theme.IndustrialDark
import com.commu.printeragent.ui.theme.IndustrialPanel
import com.commu.printeragent.ui.theme.IndustrialSurface
import com.commu.printeragent.ui.theme.IndustrialText
import com.commu.printeragent.ui.theme.IndustrialTextDim

@Composable
fun WizardScreen(modifier: Modifier = Modifier) {
    val wizardVm: WizardViewModel = viewModel()
    val state by wizardVm.uiState.collectAsState()

    if (state.isLoading) {
        Box(modifier = modifier.background(IndustrialDark), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = IndustrialBlue)
        }
        return
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier = modifier.background(IndustrialDark)) {
            WizardHeaderBar(currentStep = state.currentStep)
            StepIndicatorRow(state = state)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (state.currentStep) {
                    1 -> Step1ConnectionContent(wizardVm = wizardVm)
                    2 -> Step2DiscoveryContent(wizardVm = wizardVm)
                    3 -> Step3StaticIpContent()
                    4 -> Step4TestPrintContent()
                    else -> {}
                }
            }
            WizardNavBar(
                currentStep = state.currentStep,
                canGoBack = state.currentStep > 1,
                canGoNext = when (state.currentStep) {
                    1 -> state.step1Complete
                    2 -> state.step2Complete
                    3 -> state.step3Complete
                    4 -> state.step4Complete
                    else -> false
                },
                isLastStep = state.currentStep == 4,
                onBack = { wizardVm.goBack() },
                onNext = { wizardVm.goNext() },
                onComplete = { wizardVm.onSetupComplete() },
            )
        }
    }
}

@Composable
private fun WizardHeaderBar(currentStep: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IndustrialPanel)
            .border(1.dp, IndustrialBorder)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "שלב $currentStep מתוך 4",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = IndustrialBlue,
        )
        Text(
            text = "COMMU PRINTER AGENT",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = IndustrialTextDim,
        )
    }
}

@Composable
private fun StepIndicatorRow(state: WizardUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IndustrialSurface)
            .border(1.dp, IndustrialBorder)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (step in 1..4) {
            val isActive = step == state.currentStep
            val isDone = step < state.currentStep
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        when {
                            isActive -> IndustrialBlue
                            isDone   -> IndustrialBlue.copy(alpha = 0.4f)
                            else     -> IndustrialPanel
                        }
                    )
                    .border(
                        1.dp,
                        if (isActive) IndustrialBlue else IndustrialBorder,
                    )
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = step.toString(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive || isDone) Color.White else IndustrialTextDim,
                )
            }
        }
    }
}

@Composable
private fun WizardNavBar(
    currentStep: Int,
    canGoBack: Boolean,
    canGoNext: Boolean,
    isLastStep: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onComplete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IndustrialPanel)
            .border(1.dp, IndustrialBorder)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onBack,
            enabled = canGoBack,
            modifier = Modifier.weight(1f),
            shape = RectangleShape,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = IndustrialText,
                disabledContentColor = IndustrialTextDim,
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (canGoBack) IndustrialBorder else IndustrialBorder.copy(alpha = 0.4f),
            ),
        ) {
            Text(
                "חזרה",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
        }

        Button(
            onClick = if (isLastStep) onComplete else onNext,
            enabled = canGoNext,
            modifier = Modifier.weight(2f),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = IndustrialBlue,
                contentColor = Color.White,
                disabledContainerColor = IndustrialSurface,
                disabledContentColor = IndustrialTextDim,
            ),
        ) {
            Text(
                if (isLastStep) "סיום הגדרה" else "המשך",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}
