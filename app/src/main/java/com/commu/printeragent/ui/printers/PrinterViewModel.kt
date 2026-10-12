package com.commu.printeragent.ui.printers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.commu.printeragent.data.AppSettings
import com.commu.printeragent.data.BackendApi
import com.commu.printeragent.data.PrinterMappingRepository
import com.commu.printeragent.data.PrinterRecord
import com.commu.printeragent.data.PrinterRecord.Companion.applyEdit
import com.commu.printeragent.data.PrinterRepository
import com.commu.printeragent.data.SecureTokenStorage
import com.commu.printeragent.printing.EscPosEncoder
import com.commu.printeragent.printing.LanScanner
import com.commu.printeragent.printing.LocalNetworkInfo
import com.commu.printeragent.printing.PrintResult
import com.commu.printeragent.printing.PrinterChannel
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

sealed class SyncStatus {
    object Idle : SyncStatus()
    data class Success(val message: String) : SyncStatus()
    data class Partial(val message: String) : SyncStatus()
    data class Failure(val message: String) : SyncStatus()
}

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

    // Backend sync
    val isSyncing: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val backendMappings: Map<Int, String> = emptyMap(),  // backendId → localPrinterUUID

    // Direct print test
    val printingIds: Set<String> = emptySet(),
    val printResults: Map<String, String> = emptyMap(),  // localPrinterUUID → last result message
)

class PrinterViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = PrinterRepository(app)
    private val mappingRepo = PrinterMappingRepository(app)
    private val backendApi = BackendApi()
    private val appSettings = AppSettings(app)
    private val tokenStorage = SecureTokenStorage(app)

    private val _uiState = MutableStateFlow(PrinterUiState())
    val uiState: StateFlow<PrinterUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        refreshNetworkInfo()
        loadPrinters()
        loadMappings()
    }

    private fun loadMappings() {
        viewModelScope.launch {
            try {
                val maps = mappingRepo.loadAll()
                _uiState.update { it.copy(backendMappings = maps) }
            } catch (_: Exception) { /* non-critical on startup */ }
        }
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
                mappingRepo.removeByLocalId(id)
                reloadPrinters()
                _uiState.update { state ->
                    state.copy(backendMappings = state.backendMappings.filterValues { it != id })
                }
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

    // ── Backend sync ──────────────────────────────────────────────────────────

    fun syncWithBackend() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncStatus = SyncStatus.Idle) }
            try {
                val url = appSettings.getBackendUrl()?.takeIf { it.isNotBlank() }
                    ?: run {
                        _uiState.update { it.copy(isSyncing = false, syncStatus = SyncStatus.Failure("כתובת שרת לא מוגדרת — עבור למסך חיבור")) }
                        return@launch
                    }
                val tenantId = appSettings.getTenantId()?.takeIf { it.isNotBlank() }
                    ?: run {
                        _uiState.update { it.copy(isSyncing = false, syncStatus = SyncStatus.Failure("מזהה דייר לא מוגדר — עבור למסך חיבור")) }
                        return@launch
                    }
                val token = tokenStorage.getToken()?.takeIf { it.isNotBlank() }
                    ?: run {
                        _uiState.update { it.copy(isSyncing = false, syncStatus = SyncStatus.Failure("אסימון סוכן לא מוגדר — עבור למסך חיבור")) }
                        return@launch
                    }

                val printers = _uiState.value.savedPrinters
                if (printers.isEmpty()) {
                    _uiState.update { it.copy(isSyncing = false, syncStatus = SyncStatus.Failure("אין מדפסות שמורות לסנכרון")) }
                    return@launch
                }

                // Reject duplicate names — backend requires unique names per tenant
                val duplicates = printers.map { it.name.trim().lowercase() }
                    .groupBy { it }.filter { it.value.size > 1 }.keys
                if (duplicates.isNotEmpty()) {
                    _uiState.update {
                        it.copy(isSyncing = false, syncStatus = SyncStatus.Failure("שמות מדפסת כפולים: ${duplicates.joinToString(", ")}"))
                    }
                    return@launch
                }

                val names = printers.map { it.name.trim() }
                val primaryName = printers.firstOrNull { it.isPrimary }?.name?.trim()

                // POST agent-sync to register printer names with the backend
                val syncResult = backendApi.agentSync(url, tenantId, token, names, primaryName)
                if (syncResult.isFailure) {
                    _uiState.update {
                        it.copy(isSyncing = false, syncStatus = SyncStatus.Failure(syncResult.exceptionOrNull()?.message ?: "שגיאת סנכרון"))
                    }
                    return@launch
                }

                // GET active printers to retrieve backend IDs
                val activeResult = backendApi.fetchActivePrinters(url, tenantId, token)
                if (activeResult.isFailure) {
                    _uiState.update {
                        it.copy(isSyncing = false, syncStatus = SyncStatus.Failure(activeResult.exceptionOrNull()?.message ?: "שגיאה בקבלת מדפסות"))
                    }
                    return@launch
                }
                val backendPrinters = activeResult.getOrThrow()

                // Match local printers to backend printers by name (case-insensitive)
                val newMappings = mutableMapOf<Int, String>()
                val unmapped = mutableListOf<String>()
                for (local in printers) {
                    val match = backendPrinters.firstOrNull { bp ->
                        bp.name.trim().equals(local.name.trim(), ignoreCase = true)
                    }
                    if (match != null) newMappings[match.id] = local.id
                    else unmapped.add(local.name)
                }

                mappingRepo.saveAll(newMappings)

                val status = when {
                    unmapped.isEmpty() -> SyncStatus.Success("סנכרון הצליח — ${newMappings.size} מדפסות ממופות")
                    newMappings.isEmpty() -> SyncStatus.Failure("אף מדפסת לא ממופה — בדוק ששמות המדפסות תואמים לשרת")
                    else -> SyncStatus.Partial("${newMappings.size} ממופות · לא נמצאו בשרת: ${unmapped.joinToString(", ")}")
                }

                _uiState.update { it.copy(isSyncing = false, syncStatus = status, backendMappings = newMappings) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSyncing = false, syncStatus = SyncStatus.Failure(e.message ?: "שגיאה לא ידועה")) }
            }
        }
    }

    fun clearSyncStatus() {
        _uiState.update { it.copy(syncStatus = SyncStatus.Idle) }
    }

    // ── Direct print test ─────────────────────────────────────────────────────

    fun testPrint(id: String) {
        if (id in _uiState.value.printingIds) return
        val record = _uiState.value.savedPrinters.find { it.id == id } ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(printingIds = it.printingIds + id) }
            val bytes = EscPosEncoder.buildTestReceipt(printerName = record.name)
            val result = PrinterChannel.send(record.address, record.port, bytes)
            val message = when (result) {
                is PrintResult.Transmitted ->
                    "✓ ${result.byteCount} bytes נשלחו — נייר לא מאושר (ESC/POS)"
                is PrintResult.Failed ->
                    "✗ ${result.reason}"
            }
            _uiState.update { state ->
                state.copy(
                    printingIds = state.printingIds - id,
                    printResults = state.printResults + (id to message),
                )
            }
        }
    }

    fun clearPrintResult(id: String) {
        _uiState.update { it.copy(printResults = it.printResults - id) }
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
