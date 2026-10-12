package com.commu.printeragent.ui.connection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.commu.printeragent.data.AppSettings
import com.commu.printeragent.data.BackendApi
import com.commu.printeragent.data.SecureTokenStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectionUiState(
    val backendUrl: String = "",
    val tenantId: String = "",
    val hasStoredToken: Boolean = false,
    val isLoading: Boolean = false,
    val testResult: TestResult = TestResult.Idle,
    val isSaved: Boolean = false,
    val isSettingsLoaded: Boolean = false,
    /** True when the Keystore or encrypted prefs cannot be opened. */
    val keystoreError: Boolean = false,
)

sealed class TestResult {
    object Idle : TestResult()
    /** Connection test passed. isSaved indicates whether all persistence also succeeded. */
    object Success : TestResult()
    data class Failure(val message: String) : TestResult()
}

class ConnectionViewModel(app: Application) : AndroidViewModel(app) {

    private val appSettings = AppSettings(app)
    private val tokenStorage = SecureTokenStorage(app)
    private val backendApi = BackendApi()

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // DataStore and Keystore are loaded independently so a failure in one does not
            // suppress the other. isSettingsLoaded is always set to true so the screen
            // never remains indefinitely blank.
            var url = ""
            var tenantId = ""
            var settingsReadError = false
            try {
                url = appSettings.getBackendUrl() ?: ""
                tenantId = appSettings.getTenantId() ?: ""
            } catch (e: Exception) {
                settingsReadError = true
            }

            var hasToken = false
            var keystoreErr = false
            try {
                hasToken = tokenStorage.hasToken()
            } catch (e: Exception) {
                keystoreErr = true
            }

            _uiState.update {
                it.copy(
                    backendUrl = url,
                    tenantId = tenantId,
                    hasStoredToken = hasToken,
                    keystoreError = keystoreErr,
                    isSettingsLoaded = true,
                    testResult = if (settingsReadError)
                        TestResult.Failure("שגיאה בקריאת הגדרות — נסה שנית")
                    else
                        TestResult.Idle,
                )
            }
        }
    }

    /**
     * Runs the connection test and, on success, saves all settings.
     * isSaved is only set to true when BOTH DataStore and token storage succeeded.
     * If rawToken is blank, the stored token is used; a new token is saved only when provided.
     */
    fun testAndSave(backendUrl: String, tenantId: String, rawToken: String) {
        validate(backendUrl, tenantId)?.let { error ->
            _uiState.update { it.copy(testResult = TestResult.Failure(error)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, testResult = TestResult.Idle) }

            val effectiveToken = try {
                rawToken.trim().takeIf { it.isNotEmpty() } ?: tokenStorage.getToken() ?: ""
            } catch (e: Exception) {
                // Keystore unavailable; surface it and stop — no network call without a token.
                _uiState.update { it.copy(isLoading = false, keystoreError = true) }
                return@launch
            }

            if (effectiveToken.isEmpty()) {
                _uiState.update {
                    it.copy(isLoading = false, testResult = TestResult.Failure("נדרש אסימון סוכן"))
                }
                return@launch
            }

            backendApi.testConnection(backendUrl.trim(), tenantId.trim(), effectiveToken)
                .onSuccess {
                    // Save URL + Tenant ID
                    val settingsSaved = try {
                        appSettings.save(backendUrl.trim(), tenantId.trim())
                        true
                    } catch (e: Exception) {
                        false
                    }

                    // Save new token only when the user provided one
                    val tokenSaveNeeded = rawToken.isNotBlank()
                    val tokenSaved = if (tokenSaveNeeded) {
                        try {
                            tokenStorage.saveToken(rawToken.trim())
                            true
                        } catch (e: Exception) {
                            false
                        }
                    } else true  // no new token supplied — not a save failure

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            testResult = TestResult.Success,
                            // True only when every persistence operation succeeded
                            isSaved = settingsSaved && tokenSaved,
                            hasStoredToken = (tokenSaved && tokenSaveNeeded) || it.hasStoredToken,
                            keystoreError = !tokenSaved && tokenSaveNeeded,
                            // Only update displayed URL/tenant when they were actually persisted
                            backendUrl = if (settingsSaved) backendUrl.trim() else it.backendUrl,
                            tenantId = if (settingsSaved) tenantId.trim() else it.tenantId,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            testResult = TestResult.Failure(e.message ?: "שגיאה לא ידועה"),
                        )
                    }
                }
        }
    }

    /** Persists URL and Tenant ID (and new token if provided) without a connection test. */
    fun saveWithoutTest(backendUrl: String, tenantId: String, rawToken: String) {
        validate(backendUrl, tenantId)?.let { error ->
            _uiState.update { it.copy(testResult = TestResult.Failure(error)) }
            return
        }
        viewModelScope.launch {
            val settingsSaved = try {
                appSettings.save(backendUrl.trim(), tenantId.trim())
                true
            } catch (e: Exception) {
                false
            }

            if (!settingsSaved) {
                _uiState.update {
                    it.copy(testResult = TestResult.Failure("שגיאה בשמירת הגדרות — נסה שנית"))
                }
                return@launch
            }

            val tokenSaveNeeded = rawToken.isNotBlank()
            val tokenSaved = if (tokenSaveNeeded) {
                try {
                    tokenStorage.saveToken(rawToken.trim())
                    true
                } catch (e: Exception) {
                    _uiState.update { it.copy(keystoreError = true) }
                    false
                }
            } else true

            _uiState.update {
                it.copy(
                    isSaved = tokenSaved,
                    hasStoredToken = if (tokenSaved && tokenSaveNeeded) true else it.hasStoredToken,
                    backendUrl = backendUrl.trim(),
                    tenantId = tenantId.trim(),
                    testResult = if (!tokenSaved && tokenSaveNeeded)
                        TestResult.Failure("URL ומזהה דייר נשמרו — שגיאה בשמירת אסימון")
                    else
                        TestResult.Idle,
                )
            }
        }
    }

    /** Deletes the encrypted token file and clears the error flag — only if deletion succeeds. */
    fun resetCredentials() {
        viewModelScope.launch {
            val resetSucceeded = try {
                tokenStorage.resetStorage()
            } catch (e: Exception) {
                false
            }
            if (resetSucceeded) {
                _uiState.update {
                    it.copy(hasStoredToken = false, keystoreError = false, testResult = TestResult.Idle)
                }
            } else {
                _uiState.update {
                    it.copy(
                        testResult = TestResult.Failure("איפוס מאגר המפתחות נכשל — נסה להתקין מחדש")
                    )
                }
            }
        }
    }

    fun clearTestResult() {
        _uiState.update { it.copy(testResult = TestResult.Idle, isSaved = false) }
    }

    // ── Validation ──────────────────────────────────────────────────────────

    private fun validate(backendUrl: String, tenantId: String): String? {
        if (backendUrl.isBlank())
            return "כתובת שרת נדרשת"
        if (!backendUrl.startsWith("http://") && !backendUrl.startsWith("https://"))
            return "כתובת חייבת להתחיל ב-http:// או https://"
        if (tenantId.isBlank())
            return "מזהה דייר נדרש"
        if (tenantId.contains(Regex("[/?#\\\\]")))
            return "מזהה דייר מכיל תווים לא חוקיים (/ ? # \\)"
        return null
    }
}
