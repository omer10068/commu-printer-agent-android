package com.commu.printeragent.ui.dashboard

import android.app.Application
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.commu.printeragent.agent.AgentState
import com.commu.printeragent.agent.AgentStatus
import com.commu.printeragent.agent.PrintAgentService
import com.commu.printeragent.data.AppSettings
import com.commu.printeragent.data.BackendApi
import com.commu.printeragent.data.PrinterMappingRepository
import com.commu.printeragent.data.PrinterRecord
import com.commu.printeragent.data.PrinterRepository
import com.commu.printeragent.data.SecureTokenStorage
import com.commu.printeragent.data.TestPrintStatus
import com.commu.printeragent.printing.EscPosEncoder
import com.commu.printeragent.printing.LanScanner
import com.commu.printeragent.printing.PrintResult
import com.commu.printeragent.printing.PrinterChannel
import com.commu.printeragent.ui.onboarding.step4.PrintRowStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val businessName: String = "",
    val printerCount: Int = 0,
    val agentStatus: AgentStatus = AgentStatus.Stopped,
    val agentSessionPrinted: Int = 0,
    val agentSessionFailed: Int = 0,
    // Section expansion
    val section1Expanded: Boolean = false,
    val section2Expanded: Boolean = true,
    val section3Expanded: Boolean = false,
    // Connection editing (section 1)
    val connectionUrl: String = "",
    val connectionTenantId: String = "",
    val connectionToken: String = "",
    val connectionHasToken: Boolean = false,
    val connectionChanged: Boolean = false,
    val connectionSaving: Boolean = false,
    val connectionSaveStatus: String? = null,
    // Printers (section 2)
    val savedPrinters: List<PrinterRecord> = emptyList(),
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val scanTotal: Int = 0,
    val scanCandidates: List<String> = emptyList(),
    val showSaveSheet: Boolean = false,
    val saveSheetIp: String = "",
    val saveSheetName: String = "",
    val saveSheetError: String? = null,
    val deleteConfirmPrinterId: String? = null,
    // Test print (section 3)
    val testRowStatus: Map<String, PrintRowStatus> = emptyMap(),
    val testDialogPrinterId: String? = null,
    val testToastMessage: String? = null,
)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val appSettings = AppSettings(app)
    private val tokenStorage = SecureTokenStorage(app)
    private val printerRepo = PrinterRepository(app)
    private val mappingRepo = PrinterMappingRepository(app)
    private val backendApi = BackendApi()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            printerRepo.printerFlow.collect { printers ->
                _uiState.update { it.copy(savedPrinters = printers, printerCount = printers.size) }
            }
        }
        viewModelScope.launch {
            AgentState.status.collect { s ->
                _uiState.update { it.copy(agentStatus = s) }
            }
        }
        viewModelScope.launch {
            AgentState.sessionPrinted.collect { n ->
                _uiState.update { it.copy(agentSessionPrinted = n) }
            }
        }
        viewModelScope.launch {
            AgentState.sessionFailed.collect { n ->
                _uiState.update { it.copy(agentSessionFailed = n) }
            }
        }
        viewModelScope.launch {
            try {
                val tenantId = appSettings.getTenantId() ?: ""
                val url = appSettings.getBackendUrl() ?: ""
                val hasToken = try { tokenStorage.hasToken() } catch (_: Exception) { false }
                _uiState.update {
                    it.copy(
                        businessName = tenantId,
                        connectionUrl = url,
                        connectionTenantId = tenantId,
                        connectionHasToken = hasToken,
                    )
                }
            } catch (_: Exception) { /* non-critical */ }
        }
    }

    // ── Section toggles ───────────────────────────────────────────────────────

    fun toggleSection1() = _uiState.update { it.copy(section1Expanded = !it.section1Expanded) }
    fun toggleSection2() = _uiState.update { it.copy(section2Expanded = !it.section2Expanded) }
    fun toggleSection3() = _uiState.update { it.copy(section3Expanded = !it.section3Expanded) }

    // ── Connection editing ────────────────────────────────────────────────────

    fun updateConnectionUrl(v: String) =
        _uiState.update { it.copy(connectionUrl = v, connectionChanged = true, connectionSaveStatus = null) }

    fun updateConnectionTenantId(v: String) =
        _uiState.update { it.copy(connectionTenantId = v, connectionChanged = true, connectionSaveStatus = null) }

    fun updateConnectionToken(v: String) =
        _uiState.update { it.copy(connectionToken = v, connectionChanged = true, connectionSaveStatus = null) }

    fun saveConnection(url: String, tenantId: String, token: String) {
        if (url.isBlank()) {
            _uiState.update { it.copy(connectionSaveStatus = "כתובת שרת נדרשת") }
            return
        }
        if (tenantId.isBlank()) {
            _uiState.update { it.copy(connectionSaveStatus = "מזהה דייר נדרש") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(connectionSaving = true, connectionSaveStatus = null) }
            val effectiveToken = try {
                token.trim().takeIf { it.isNotEmpty() } ?: tokenStorage.getToken() ?: ""
            } catch (_: Exception) {
                _uiState.update { it.copy(connectionSaving = false, connectionSaveStatus = "שגיאת מאגר מפתחות") }
                return@launch
            }
            if (effectiveToken.isEmpty()) {
                _uiState.update { it.copy(connectionSaving = false, connectionSaveStatus = "נדרש אסימון סוכן") }
                return@launch
            }
            val testResult = backendApi.testConnection(url.trim(), tenantId.trim(), effectiveToken)
            if (testResult.isFailure) {
                _uiState.update {
                    it.copy(
                        connectionSaving = false,
                        connectionSaveStatus = "בדיקה נכשלה: ${testResult.exceptionOrNull()?.message}",
                    )
                }
                return@launch
            }
            try {
                appSettings.save(url.trim(), tenantId.trim())
            } catch (_: Exception) {
                _uiState.update { it.copy(connectionSaving = false, connectionSaveStatus = "שגיאה בשמירת הגדרות") }
                return@launch
            }
            if (token.isNotBlank()) {
                try {
                    tokenStorage.saveToken(token.trim())
                } catch (_: Exception) {
                    _uiState.update { it.copy(connectionSaving = false, connectionSaveStatus = "הגדרות נשמרו — שגיאה בשמירת אסימון") }
                    return@launch
                }
            }
            _uiState.update {
                it.copy(
                    connectionSaving = false,
                    connectionChanged = false,
                    connectionSaveStatus = "✓ החיבור נשמר בהצלחה",
                    businessName = tenantId.trim(),
                )
            }
        }
    }

    fun clearConnectionSaveStatus() =
        _uiState.update { it.copy(connectionSaveStatus = null) }

    // ── Agent control ─────────────────────────────────────────────────────────

    fun startAgent() {
        ContextCompat.startForegroundService(
            getApplication(),
            PrintAgentService.startIntent(getApplication()),
        )
    }

    fun stopAgent() {
        getApplication<Application>().startService(PrintAgentService.stopIntent(getApplication()))
    }

    // ── LAN scan ──────────────────────────────────────────────────────────────

    fun startScan() {
        if (_uiState.value.isScanning) return
        val networkInfo = LanScanner.localNetworkInfo(getApplication())
        if (networkInfo == null) {
            _uiState.update { it.copy(syncMessage = "לא נמצא ממשק רשת פעיל — בדוק חיבור Wi-Fi") }
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
                // user cancellation
            } catch (e: Exception) {
                _uiState.update { it.copy(syncMessage = "שגיאת סריקה: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(isScanning = false) }
    }

    // ── Save sheet ────────────────────────────────────────────────────────────

    fun showSaveSheet(ip: String) {
        _uiState.update { it.copy(showSaveSheet = true, saveSheetIp = ip, saveSheetName = "", saveSheetError = null) }
    }

    fun hideSaveSheet() {
        _uiState.update { it.copy(showSaveSheet = false, saveSheetName = "", saveSheetError = null) }
    }

    fun updateSaveSheetName(name: String) {
        _uiState.update { it.copy(saveSheetName = name, saveSheetError = null) }
    }

    fun saveScanResult() {
        val state = _uiState.value
        if (state.saveSheetName.isBlank()) {
            _uiState.update { it.copy(saveSheetError = "חובה לתת שם למדפסת") }
            return
        }
        viewModelScope.launch {
            try {
                val isFirst = state.savedPrinters.isEmpty()
                val record = PrinterRecord(
                    id        = java.util.UUID.randomUUID().toString(),
                    name      = state.saveSheetName.trim(),
                    address   = state.saveSheetIp,
                    port      = 9100,
                    isPrimary = isFirst,
                )
                printerRepo.save(record)
                _uiState.update { it.copy(showSaveSheet = false, saveSheetName = "", saveSheetError = null) }
                // Auto-sync after saving
                syncWithBackend()
            } catch (_: Exception) {
                _uiState.update { it.copy(saveSheetError = "שגיאה בשמירה — נסה שנית") }
            }
        }
    }

    private suspend fun syncWithBackend() {
        _uiState.update { it.copy(isSyncing = true, syncMessage = null) }
        try {
            val url = appSettings.getBackendUrl()?.takeIf { it.isNotBlank() } ?: run {
                _uiState.update { it.copy(isSyncing = false, syncMessage = "כתובת שרת לא מוגדרת") }
                return
            }
            val tenantId = appSettings.getTenantId()?.takeIf { it.isNotBlank() } ?: run {
                _uiState.update { it.copy(isSyncing = false, syncMessage = "מזהה דייר לא מוגדר") }
                return
            }
            val token = tokenStorage.getToken()?.takeIf { it.isNotBlank() } ?: run {
                _uiState.update { it.copy(isSyncing = false, syncMessage = "אסימון לא מוגדר") }
                return
            }
            val printers = printerRepo.loadAll()
            if (printers.isEmpty()) {
                _uiState.update { it.copy(isSyncing = false) }
                return
            }
            val names = printers.map { it.name.trim() }
            val primaryName = printers.firstOrNull { it.isPrimary }?.name?.trim()
            val syncResult = backendApi.agentSync(url, tenantId, token, names, primaryName)
            if (syncResult.isFailure) {
                _uiState.update { it.copy(isSyncing = false, syncMessage = "שגיאת סנכרון: ${syncResult.exceptionOrNull()?.message}") }
                return
            }
            val activeResult = backendApi.fetchActivePrinters(url, tenantId, token)
            if (activeResult.isSuccess) {
                val backendPrinters = activeResult.getOrThrow()
                val newMappings = mutableMapOf<Int, String>()
                for (local in printers) {
                    val match = backendPrinters.firstOrNull { bp ->
                        bp.name.trim().equals(local.name.trim(), ignoreCase = true)
                    }
                    if (match != null) newMappings[match.id] = local.id
                }
                mappingRepo.saveAll(newMappings)
                _uiState.update { it.copy(isSyncing = false, syncMessage = "✓ סנכרון הצליח — ${newMappings.size} מדפסות") }
            } else {
                _uiState.update { it.copy(isSyncing = false, syncMessage = "הרשמה הצליחה — לא ניתן לאחזר מדפסות") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isSyncing = false, syncMessage = "שגיאה: ${e.message}") }
        }
    }

    fun clearSyncMessage() = _uiState.update { it.copy(syncMessage = null) }

    // ── Printer deletion ──────────────────────────────────────────────────────

    /** Shows a confirmation dialog for removing the printer with the given UUID. */
    fun requestDeletePrinter(id: String) =
        _uiState.update { it.copy(deleteConfirmPrinterId = id) }

    /** Dismisses the delete confirmation dialog without performing any action. */
    fun cancelDeletePrinter() =
        _uiState.update { it.copy(deleteConfirmPrinterId = null) }

    /**
     * Permanently removes the confirmed printer from local storage and its backend-ID mapping,
     * then re-syncs the remaining printers with the backend.
     *
     * The printer is identified by its UUID, never by name or IP address alone.
     */
    fun confirmDeletePrinter() {
        val id = _uiState.value.deleteConfirmPrinterId ?: return
        _uiState.update { it.copy(deleteConfirmPrinterId = null) }
        viewModelScope.launch {
            try {
                printerRepo.delete(id)
                mappingRepo.removeByLocalId(id)
                syncWithBackend()
            } catch (e: Exception) {
                _uiState.update { it.copy(syncMessage = "שגיאה בהסרת מדפסת: ${e.message}") }
            }
        }
    }

    // ── Test print ────────────────────────────────────────────────────────────

    fun sendTestPrint(printer: PrinterRecord) {
        if (_uiState.value.testRowStatus[printer.id] == PrintRowStatus.Printing) return
        viewModelScope.launch {
            _uiState.update { it.copy(testRowStatus = it.testRowStatus + (printer.id to PrintRowStatus.Printing)) }
            val bytes = EscPosEncoder.buildTestReceipt(printer.name)
            val result = PrinterChannel.send(printer.address, printer.port, bytes)
            when (result) {
                is PrintResult.Transmitted -> {
                    _uiState.update { it.copy(testRowStatus = it.testRowStatus + (printer.id to PrintRowStatus.Idle)) }
                    if (printer.testPrintStatus == TestPrintStatus.Verified) {
                        _uiState.update { it.copy(testToastMessage = "דף בדיקה נשלח") }
                    } else {
                        _uiState.update { it.copy(testDialogPrinterId = printer.id) }
                    }
                }
                is PrintResult.Failed -> {
                    _uiState.update { it.copy(testRowStatus = it.testRowStatus + (printer.id to PrintRowStatus.TcpFailed)) }
                }
            }
        }
    }

    fun onPhysicalTestConfirm(printerId: String, confirmed: Boolean) {
        _uiState.update { it.copy(testDialogPrinterId = null) }
        val status = if (confirmed) TestPrintStatus.Verified else TestPrintStatus.Failed
        viewModelScope.launch {
            printerRepo.updateTestPrintStatus(printerId, status)
        }
    }

    fun clearTestToast() = _uiState.update { it.copy(testToastMessage = null) }
}
