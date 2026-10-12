package com.commu.printeragent.ui.jobs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.commu.printeragent.agent.JobProcessor
import com.commu.printeragent.agent.JobResult
import com.commu.printeragent.data.AppSettings
import com.commu.printeragent.data.BackendApi
import com.commu.printeragent.data.PrinterMappingRepository
import com.commu.printeragent.data.PrinterRepository
import com.commu.printeragent.data.SecureTokenStorage
import com.commu.printeragent.printing.EscPosEncoder
import com.commu.printeragent.printing.PrinterChannel
import com.commu.printeragent.printing.ReceiptRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class JobQueueUiState(
    val isProcessing: Boolean = false,
    val statusLine: String? = null,
    val errorMessage: String? = null,
    val jobResults: List<JobResult> = emptyList(),
    val lastRunMs: Long = 0L,
    val printedCount: Int = 0,
    val failedCount: Int = 0,
)

class JobQueueViewModel(app: Application) : AndroidViewModel(app) {

    private val appSettings = AppSettings(app)
    private val tokenStorage = SecureTokenStorage(app)
    private val mappingRepo = PrinterMappingRepository(app)
    private val printerRepo = PrinterRepository(app)
    private val api = BackendApi()

    private val _uiState = MutableStateFlow(JobQueueUiState())
    val uiState: StateFlow<JobQueueUiState> = _uiState.asStateFlow()

    fun processJobQueue() {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusLine = "טוען הגדרות…",
                    errorMessage = null,
                    jobResults = emptyList(),
                )
            }
            try {
                val url = appSettings.getBackendUrl()?.takeIf { it.isNotBlank() }
                    ?: run {
                        _uiState.update {
                            it.copy(isProcessing = false, errorMessage = "כתובת שרת לא מוגדרת — עבור למסך חיבור")
                        }
                        return@launch
                    }
                val tenantId = appSettings.getTenantId()?.takeIf { it.isNotBlank() }
                    ?: run {
                        _uiState.update {
                            it.copy(isProcessing = false, errorMessage = "מזהה דייר לא מוגדר — עבור למסך חיבור")
                        }
                        return@launch
                    }
                val token = tokenStorage.getToken()?.takeIf { it.isNotBlank() }
                    ?: run {
                        _uiState.update {
                            it.copy(isProcessing = false, errorMessage = "אסימון סוכן לא מוגדר — עבור למסך חיבור")
                        }
                        return@launch
                    }

                val mappings = mappingRepo.loadAll()
                if (mappings.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "אין מדפסות ממופות — עבור למסך מדפסות וסנכרן עם השרת",
                        )
                    }
                    return@launch
                }

                val printers = printerRepo.loadAll()
                _uiState.update { it.copy(statusLine = "שולח שאילתה לשרת…") }

                val processor = JobProcessor(
                    fetchQueue = { bUrl, tId, tok, ids -> api.fetchJobQueue(bUrl, tId, tok, ids) },
                    patchStatus = { bUrl, tId, tok, jId, status, err ->
                        api.patchJobStatus(bUrl, tId, tok, jId, status, err)
                    },
                    send = { addr, port, bytes -> PrinterChannel.send(addr, port, bytes) },
                    render = { text ->
                        withContext(Dispatchers.IO) {
                            val rasterBytes = ReceiptRenderer.renderText(text)
                            EscPosEncoder.buildRasterReceipt(rasterBytes)
                        }
                    },
                )

                val results = processor.processQueue(
                    baseUrl = url,
                    tenantId = tenantId,
                    token = token,
                    mappings = mappings,
                    printers = printers,
                    onProgress = { msg -> _uiState.update { it.copy(statusLine = msg) } },
                )

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusLine = if (results.isEmpty()) "אין עבודות בתור" else null,
                        jobResults = results,
                        lastRunMs = System.currentTimeMillis(),
                        printedCount = results.count { r -> r.isSuccess },
                        failedCount = results.count { r -> !r.isSuccess },
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusLine = null,
                        errorMessage = e.message ?: "שגיאה לא ידועה",
                    )
                }
            }
        }
    }

    fun clearResults() {
        _uiState.update {
            it.copy(jobResults = emptyList(), statusLine = null, errorMessage = null)
        }
    }
}
