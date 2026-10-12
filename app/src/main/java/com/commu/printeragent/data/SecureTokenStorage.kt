package com.commu.printeragent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keystore-backed token storage using EncryptedSharedPreferences (security-crypto 1.0.0).
 * All operations that touch the Keystore or the encrypted file run on Dispatchers.IO.
 * Initialization is guarded by a Mutex so it occurs exactly once even under concurrent access.
 */
class SecureTokenStorage(context: Context) {

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var cachedPrefs: SharedPreferences? = null

    /**
     * Returns the SharedPreferences instance, initializing it on first call.
     * Throws if the Keystore is unavailable or the encrypted file is corrupt.
     */
    private suspend fun prefs(): SharedPreferences = mutex.withLock {
        cachedPrefs ?: withContext(Dispatchers.IO) {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_FILE,
                masterKeyAlias,
                appContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.also { cachedPrefs = it }
    }

    /** Saves the token. Never logs the value. Throws on Keystore failure. */
    suspend fun saveToken(token: String) = withContext(Dispatchers.IO) {
        prefs().edit().putString(KEY_TOKEN, token).apply()
    }

    /** Returns the stored token, or null if none. Never logs the value. Throws on Keystore failure. */
    suspend fun getToken(): String? = withContext(Dispatchers.IO) {
        prefs().getString(KEY_TOKEN, null)
    }

    /** Returns true if a token has been saved. Throws on Keystore failure. */
    suspend fun hasToken(): Boolean = withContext(Dispatchers.IO) {
        prefs().contains(KEY_TOKEN)
    }

    /** Clears the stored token. Throws on Keystore failure. */
    suspend fun clearToken() = withContext(Dispatchers.IO) {
        prefs().edit().remove(KEY_TOKEN).apply()
    }

    /**
     * Deletes the encrypted preferences file and resets cached state.
     * Use when Keystore initialization has failed and the user must re-enter credentials.
     * Safe to call even when prefs() would throw.
     */
    suspend fun resetStorage() = withContext(Dispatchers.IO) {
        mutex.withLock {
            cachedPrefs = null
            appContext.deleteSharedPreferences(PREFS_FILE)
        }
    }

    companion object {
        const val PREFS_FILE = "secure_agent_prefs"
        private const val KEY_TOKEN = "agent_token"
    }
}
