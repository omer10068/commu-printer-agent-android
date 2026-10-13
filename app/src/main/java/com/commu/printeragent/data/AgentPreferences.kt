package com.commu.printeragent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.agentPrefStore: DataStore<Preferences> by preferencesDataStore(name = "agent_prefs")

/**
 * Persists the user's agent enabled/disabled preference.
 *
 * Backed by regular (non-encrypted) DataStore so the value is readable even
 * before the first device unlock after a reboot, as long as the system storage
 * is accessible. On a freshly encrypted device that has not yet been unlocked,
 * [isEnabled] will return false (the safe default) rather than throw.
 */
class AgentPreferences(context: Context) {
    private val ctx = context.applicationContext
    private val keyEnabled = booleanPreferencesKey("agent_enabled")

    /** Emits the current enabled state and updates when it changes. */
    val enabledFlow: Flow<Boolean> = ctx.agentPrefStore.data
        .map { prefs -> prefs[keyEnabled] ?: false }
        .catch { emit(false) }

    /** Returns the current enabled state; returns false on any read error. */
    suspend fun isEnabled(): Boolean = try {
        ctx.agentPrefStore.data.first()[keyEnabled] ?: false
    } catch (_: Exception) {
        false
    }

    suspend fun setEnabled(enabled: Boolean) {
        ctx.agentPrefStore.edit { prefs -> prefs[keyEnabled] = enabled }
    }
}
