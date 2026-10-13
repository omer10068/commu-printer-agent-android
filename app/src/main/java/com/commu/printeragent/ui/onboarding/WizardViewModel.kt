package com.commu.printeragent.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.commu.printeragent.data.IpConfigStatus
import com.commu.printeragent.data.OnboardingRepository
import com.commu.printeragent.data.PrinterRepository
import com.commu.printeragent.data.TestPrintStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WizardUiState(
    val currentStep: Int = 1,
    val step1Complete: Boolean = false,
    val step2Complete: Boolean = false,
    val step3Complete: Boolean = false,
    val step4Complete: Boolean = false,
    val isLoading: Boolean = true,
)

class WizardViewModel(app: Application) : AndroidViewModel(app) {
    private val onboardingRepo = OnboardingRepository(app)
    private val printerRepo = PrinterRepository(app)

    private val _uiState = MutableStateFlow(WizardUiState())
    val uiState: StateFlow<WizardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val step = onboardingRepo.getCurrentStep()
            val s1 = onboardingRepo.isStep1Complete()
            val s2 = onboardingRepo.isStep2Complete()
            _uiState.update { it.copy(currentStep = step, step1Complete = s1, step2Complete = s2, isLoading = false) }
        }
        viewModelScope.launch {
            printerRepo.printerFlow.collect { printers ->
                val s3 = printers.isNotEmpty() && printers.all { it.ipConfigStatus == IpConfigStatus.Configured }
                val s4 = printers.isNotEmpty() && printers.all { it.testPrintStatus == TestPrintStatus.Verified }
                _uiState.update { it.copy(step3Complete = s3, step4Complete = s4) }
            }
        }
    }

    fun markStep1Complete() {
        viewModelScope.launch { onboardingRepo.markStep1Complete() }
        _uiState.update { it.copy(step1Complete = true) }
    }

    fun markStep2Complete() {
        viewModelScope.launch { onboardingRepo.markStep2Complete() }
        _uiState.update { it.copy(step2Complete = true) }
    }

    fun goBack() {
        val current = _uiState.value.currentStep
        if (current <= 1) return
        val newStep = current - 1
        viewModelScope.launch { onboardingRepo.setCurrentStep(newStep) }
        _uiState.update { it.copy(currentStep = newStep) }
    }

    fun goNext() {
        val state = _uiState.value
        val canAdvance = when (state.currentStep) {
            1 -> state.step1Complete
            2 -> state.step2Complete
            3 -> state.step3Complete
            else -> false
        }
        if (!canAdvance || state.currentStep >= 4) return
        val newStep = state.currentStep + 1
        viewModelScope.launch { onboardingRepo.setCurrentStep(newStep) }
        _uiState.update { it.copy(currentStep = newStep) }
    }

    fun onSetupComplete() {
        viewModelScope.launch { onboardingRepo.setComplete() }
    }
}
