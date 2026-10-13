package com.commu.printeragent.ui.onboarding.step4

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.commu.printeragent.data.PrinterRecord
import com.commu.printeragent.data.PrinterRepository
import com.commu.printeragent.data.TestPrintStatus
import com.commu.printeragent.printing.EscPosEncoder
import com.commu.printeragent.printing.PrintResult
import com.commu.printeragent.printing.PrinterChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PrintRowStatus { Idle, Printing, TcpFailed }

data class Step4UiState(
    val printers: List<PrinterRecord> = emptyList(),
    val rowStatus: Map<String, PrintRowStatus> = emptyMap(),  // printerId -> status
    val showingDialogForPrinterId: String? = null,
    val toastMessage: String? = null,
)

class Step4ViewModel(app: Application) : AndroidViewModel(app) {
    private val printerRepo = PrinterRepository(app)

    private val _uiState = MutableStateFlow(Step4UiState())
    val uiState: StateFlow<Step4UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            printerRepo.printerFlow.collect { printers ->
                _uiState.update { it.copy(printers = printers) }
            }
        }
    }

    fun sendTestPrint(printer: PrinterRecord) {
        if (_uiState.value.rowStatus[printer.id] == PrintRowStatus.Printing) return
        viewModelScope.launch {
            _uiState.update { it.copy(rowStatus = it.rowStatus + (printer.id to PrintRowStatus.Printing)) }
            val bytes = EscPosEncoder.buildTestReceipt(printer.name)
            val result = PrinterChannel.send(printer.address, printer.port, bytes)
            when (result) {
                is PrintResult.Transmitted -> {
                    _uiState.update { it.copy(rowStatus = it.rowStatus + (printer.id to PrintRowStatus.Idle)) }
                    if (printer.testPrintStatus == TestPrintStatus.Verified) {
                        // Already verified — just show toast
                        _uiState.update { it.copy(toastMessage = "דף בדיקה נשלח") }
                    } else {
                        // Show physical confirm dialog
                        _uiState.update { it.copy(showingDialogForPrinterId = printer.id) }
                    }
                }
                is PrintResult.Failed -> {
                    _uiState.update { it.copy(rowStatus = it.rowStatus + (printer.id to PrintRowStatus.TcpFailed)) }
                }
            }
        }
    }

    fun onPhysicalConfirm(printerId: String, confirmed: Boolean) {
        _uiState.update { it.copy(showingDialogForPrinterId = null) }
        val status = if (confirmed) TestPrintStatus.Verified else TestPrintStatus.Failed
        viewModelScope.launch {
            printerRepo.updateTestPrintStatus(printerId, status)
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
