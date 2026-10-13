package com.commu.printeragent.ui.onboarding.step3

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.commu.printeragent.data.IpConfigStatus
import com.commu.printeragent.data.PrinterMappingRepository
import com.commu.printeragent.data.PrinterRecord
import com.commu.printeragent.data.PrinterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Step3UiState(
    val printers: List<PrinterRecord> = emptyList(),
    val pendingBrowserPrinterId: String? = null,   // printer for which browser was launched
    val showingDialogForPrinterId: String? = null, // printer for which IP-config dialog is shown
    val deleteConfirmPrinterId: String? = null,    // printer pending delete confirmation
)

class Step3ViewModel(app: Application) : AndroidViewModel(app) {
    private val printerRepo = PrinterRepository(app)
    private val mappingRepo = PrinterMappingRepository(app)

    private val _uiState = MutableStateFlow(Step3UiState())
    val uiState: StateFlow<Step3UiState> = _uiState.asStateFlow()

    // Emits a URL to open in the browser (consumed once by the composable)
    private val _pendingBrowserUrl = MutableStateFlow<String?>(null)
    val pendingBrowserUrl: StateFlow<String?> = _pendingBrowserUrl.asStateFlow()

    init {
        viewModelScope.launch {
            printerRepo.printerFlow.collect { printers ->
                _uiState.update { it.copy(printers = printers) }
            }
        }
    }

    fun openBrowserForPrinter(printer: PrinterRecord) {
        _uiState.update { it.copy(pendingBrowserPrinterId = printer.id) }
        _pendingBrowserUrl.value = "http://${printer.address}"
    }

    fun onBrowserUrlConsumed() {
        _pendingBrowserUrl.value = null
    }

    fun onResumedFromBrowser() {
        val pendingId = _uiState.value.pendingBrowserPrinterId ?: return
        // Show dialog for the printer that was opened in browser
        _uiState.update { it.copy(showingDialogForPrinterId = pendingId, pendingBrowserPrinterId = null) }
    }

    fun onIpConfigConfirmed(printerId: String, confirmed: Boolean) {
        _uiState.update { it.copy(showingDialogForPrinterId = null) }
        val status = if (confirmed) IpConfigStatus.Configured else IpConfigStatus.Failed
        viewModelScope.launch {
            printerRepo.updateIpConfigStatus(printerId, status)
        }
    }

    // ── Printer deletion ──────────────────────────────────────────────────────

    fun requestDeletePrinter(id: String) =
        _uiState.update { it.copy(deleteConfirmPrinterId = id) }

    fun cancelDeletePrinter() =
        _uiState.update { it.copy(deleteConfirmPrinterId = null) }

    /**
     * Removes the confirmed printer by UUID from local storage and its backend-ID mapping.
     * The reactive [printerFlow] then emits the updated list, which causes [WizardViewModel]
     * to recompute step3Complete automatically.
     *
     * Backend re-sync is deferred to the Dashboard, which has access to the stored credentials.
     */
    fun confirmDeletePrinter() {
        val id = _uiState.value.deleteConfirmPrinterId ?: return
        _uiState.update { it.copy(deleteConfirmPrinterId = null) }
        viewModelScope.launch {
            printerRepo.delete(id)
            mappingRepo.removeByLocalId(id)
        }
    }
}
