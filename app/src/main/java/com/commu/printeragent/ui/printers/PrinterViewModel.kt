package com.commu.printeragent.ui.printers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.commu.printeragent.data.PrinterRecord
import com.commu.printeragent.data.PrinterRecord.Companion.applyEdit
import com.commu.printeragent.data.PrinterRepository
import com.commu.printeragent.printing.LanScanner
import com.commu.printeragent.printing.LocalNetworkInfo
import com.commu.printeragent.printing.TcpProbe
import com.commu.printeragent.printing.isValidIpv4
import com.commu.printeragent.printing.isValidPort
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class StatusMessage(val text: String, val isError: Boolean)

data class PrinterUiState(
    // Network / scan
    val localNetwork: LocalNetworkInfo? = null,
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val scanTotal: Int = 0,
    val scanCandidates: List<String> = emptyList(),

    // Saved printers
    val savedPrinters: List<PrinterRecord> = emptyList(),
    val isPrintersLoading: Boolean = true,
    val checkingIds: Set<String> = emptySet(),

    // Add / Edit form
    val formAddress: String = "",
    val formPort: String = "9100",
    val formName: String = "",
    val formNotes: String = "",
    val editingId: String? = null,
    val isFormChecking: Boolean = false,
    val formCheckMessage: String? = null,
    val formError: String? = null,

    // General feedback
    val statusMessage: StatusMessage? = null,
    val loadError: String? = null,
)

class PrinterViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = PrinterRepository(app)

    private val _uiState = MutableStateFlow(PrinterUiState())
    val uiState: StateFlow<PrinterUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        refreshNetworkInfo()
        loadPrinters()
    }

    // ── Network info ──────────────────────────────────────────────────────────

    fun refreshNetworkInfo() {
        _uiState.update { it.copy(localNetwork = LanScanner.localNetworkInfo(getApplication())) }
    }

    // ── LAN Scan ──────────────────────────────────────────────────────────────

    fun startScan() {
        if (_uiState.value.isScanning) return
        val networkInfo = _uiState.value.localNetwork
        if (networkInfo == null) {
            _uiState.update {
                it.copy(statusMessage = StatusMessage("לא נמצא ממשק רשת פעיל — בדוק חיבור Wi-Fi", true))
            }
            return
        }

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    scanProgress = 0,
                    scanTotal = networkInfo.scanHostCount,
                    scanCandidates = emptyList(),
                )
            }
            try {
                LanScanner.scan(
                    networkInfo = networkInfo,
                    onProgress = { completed, total ->
                        _uiState.update { it.copy(scanProgress = completed, scanTotal = total) }
                    },
                    onFound = { address ->
                        _uiState.update { it.copy(scanCandidates = it.scanCandidates + address) }
                    },
                )
            } catch (_: kotlinx.coroutines.CancellationException) {
                // normal user cancellation — no error message
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusMessage = StatusMessage("שגיאת סריקה: ${e.message}", true))
                }
            } finally {
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(isScanning = false) }
    }

    // ── Saved printers ────────────────────────────────────────────────────────

    private fun loadPrinters() {
        viewModelScope.launch {
            try {
                val printers = repository.loadAll()
                _uiState.update { it.copy(savedPrinters = printers, isPrintersLoading = false, loadError = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isPrintersLoading = false, loadError = "שגיאה בטעינת מדפסות — נסה לאתחל") }
            }
        }
    }

    private suspend fun reloadPrinters() {
        try {
            val printers = repository.loadAll()
            _uiState.update { it.copy(savedPrinters = printers, loadError = null) }
        } catch (e: Exception) {
            _uiState.update { it.copy(loadError = "שגיאה בטעינת רשימת המדפסות") }
            // existing savedPrinters preserved — do not overwrite with empty
        }
    }

    fun testPrinter(id: String) {
        val record = _uiState.value.savedPrinters.find { it.id == id } ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(checkingIds = it.checkingIds + id) }
            val ok = TcpProbe.check(record.address, record.port, timeoutMs = 3000)
            val nowMs = System.currentTimeMillis()
            try {
                repository.updateCheckResult(id, ok, nowMs)
            } catch (_: Exception) { /* non-critical */ }
            _uiState.update { state ->
                state.copy(
                    checkingIds = state.checkingIds - id,
                    savedPrinters = state.savedPrinters.map { p ->
                        if (p.id == id) p.copy(lastCheckMs = nowMs, lastCheckOk = ok) else p
                    },
                )
            }
        }
    }

    fun deletePrinter(id: String) {
        viewModelScope.launch {
            try {
                val wasPrimary = _uiState.value.savedPrinters.find { it.id == id }?.isPrimary == true
                repository.delete(id)
                if (wasPrimary) {
                    val next = _uiState.value.savedPrinters.firstOrNull { it.id != id }
                    if (next != null) repository.setPrimary(next.id)
                }
                reloadPrinters()
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(statusMessage = StatusMessage("שגיאה במחיקת מדפסת — נסה שנית", true))
                }
            }
        }
    }

    /** Atomically sets [id] as the primary printer, clearing the flag on all others. */
    fun setPrimary(id: String) {
        viewModelScope.launch {
            try {
                repository.setPrimary(id)
                reloadPrinters()
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(statusMessage = StatusMessage("שגיאה בעדכון מדפסת ראשית", true))
                }
            }
        }
    }

    // ── Add / Edit form ───────────────────────────────────────────────────────

    /** Pre-populates the form IP from a scan candidate; clears other fields. */
    fun prepopulateForm(address: String) {
        _uiState.update {
            it.copy(
                formAddress = address,
                formPort = "9100",
                formName = "",
                formNotes = "",
                editingId = null,
                formError = null,
                formCheckMessage = null,
            )
        }
    }

    /** Loads an existing printer into the form for editing. */
    fun startEdit(record: PrinterRecord) {
        _uiState.update {
            it.copy(
                formAddress = record.address,
                formPort = record.port.toString(),
                formName = record.name,
                formNotes = record.notes,
                editingId = record.id,
                formError = null,
                formCheckMessage = null,
            )
        }
    }

    fun clearForm() {
        _uiState.update {
            it.copy(
                formAddress = "",
                formPort = "9100",
                formName = "",
                formNotes = "",
                editingId = null,
                formError = null,
                formCheckMessage = null,
            )
        }
    }

    fun updateFormAddress(v: String) =
        _uiState.update { it.copy(formAddress = v, formError = null, formCheckMessage = null) }

    fun updateFormPort(v: String) =
        _uiState.update { it.copy(formPort = v, formError = null) }

    fun updateFormName(v: String) =
        _uiState.update { it.copy(formName = v, formError = null) }

    fun updateFormNotes(v: String) =
        _uiState.update { it.copy(formNotes = v) }

    fun testFormConnection() {
        val ip = _uiState.value.formAddress.trim()
        val port = _uiState.value.formPort.trim().toIntOrNull()
        if (!isValidIpv4(ip)) {
            _uiState.update { it.copy(formError = "כתובת IP לא תקינה") }
            return
        }
        if (port == null || !isValidPort(port)) {
            _uiState.update { it.copy(formError = "יציאה לא תקינה (1–65535)") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isFormChecking = true, formCheckMessage = null, formError = null) }
            val ok = TcpProbe.check(ip, port, timeoutMs = 3000)
            _uiState.update {
                it.copy(
                    isFormChecking = false,
                    formCheckMessage = if (ok)
                        "✓ TCP $ip:$port — נגיש"
                    else
                        "✗ TCP $ip:$port — לא נגיש או תם הזמן",
                )
            }
        }
    }

    fun savePrinter() {
        val state = _uiState.value
        val ip   = state.formAddress.trim()
        val name = state.formName.trim()
        val port = state.formPort.trim().toIntOrNull()

        if (!isValidIpv4(ip)) {
            _uiState.update { it.copy(formError = "כתובת IP לא תקינה") }
            return
        }
        if (port == null || !isValidPort(port)) {
            _uiState.update { it.copy(formError = "יציאה לא תקינה (1–65535)") }
            return
        }
        if (name.isBlank()) {
            _uiState.update { it.copy(formError = "שם מדפסת נדרש") }
            return
        }

        viewModelScope.launch {
            try {
                val isFirstPrinter = state.editingId == null && state.savedPrinters.isEmpty()
                val existingRecord = state.savedPrinters.find { it.id == state.editingId }
                val record = if (existingRecord != null) {
                    applyEdit(existingRecord, name = name, address = ip, port = port, notes = state.formNotes.trim())
                } else {
                    PrinterRecord(
                        id        = UUID.randomUUID().toString(),
                        name      = name,
                        address   = ip,
                        port      = port,
                        notes     = state.formNotes.trim(),
                        isPrimary = isFirstPrinter,
                    )
                }
                repository.save(record)
                reloadPrinters()
                clearForm()
                _uiState.update {
                    it.copy(statusMessage = StatusMessage("מדפסת נשמרה: $name", false))
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(formError = "שגיאה בשמירה — נסה שנית") }
            }
        }
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
