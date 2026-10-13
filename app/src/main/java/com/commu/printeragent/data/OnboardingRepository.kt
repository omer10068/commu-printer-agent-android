package com.commu.printeragent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.onboardingStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding_prefs")

/**
 * Persistent storage for onboarding/wizard state.
 *
 * Keys:
 *   "setup_complete"   → true when the user has finished the full wizard
 *   "wizard_step"      → current wizard step (1–4)
 *   "step1_complete"   → true when step 1 (connection) is done
 *   "step2_complete"   → true when step 2 (printer discovery) is done
 *   "migration_done"   → true when migration from old (non-wizard) installs has run
 */
class OnboardingRepository(context: Context) {

    private val ctx = context.applicationContext

    private val keySetupComplete  = booleanPreferencesKey("setup_complete")
    private val keyWizardStep     = intPreferencesKey("wizard_step")
    private val keyStep1Complete  = booleanPreferencesKey("step1_complete")
    private val keyStep2Complete  = booleanPreferencesKey("step2_complete")
    private val keyMigrationDone  = booleanPreferencesKey("migration_done")

    // ── Reactive ─────────────────────────────────────────────────────────────

    val setupCompleteFlow: Flow<Boolean> =
        ctx.onboardingStore.data.map { it[keySetupComplete] == true }

    // ── Read ─────────────────────────────────────────────────────────────────

    suspend fun isSetupComplete(): Boolean = withContext(Dispatchers.IO) {
        ctx.onboardingStore.data.first()[keySetupComplete] == true
    }

    suspend fun getCurrentStep(): Int = withContext(Dispatchers.IO) {
        ctx.onboardingStore.data.first()[keyWizardStep] ?: 1
    }

    suspend fun isStep1Complete(): Boolean = withContext(Dispatchers.IO) {
        ctx.onboardingStore.data.first()[keyStep1Complete] == true
    }

    suspend fun isStep2Complete(): Boolean = withContext(Dispatchers.IO) {
        ctx.onboardingStore.data.first()[keyStep2Complete] == true
    }

    // ── Write ────────────────────────────────────────────────────────────────

    suspend fun setComplete(): Unit = withContext(Dispatchers.IO) {
        ctx.onboardingStore.edit { prefs ->
            prefs[keySetupComplete] = true
        }
    }

    suspend fun setCurrentStep(step: Int): Unit = withContext(Dispatchers.IO) {
        ctx.onboardingStore.edit { prefs ->
            prefs[keyWizardStep] = step
        }
    }

    suspend fun markStep1Complete(): Unit = withContext(Dispatchers.IO) {
        ctx.onboardingStore.edit { prefs ->
            prefs[keyStep1Complete] = true
        }
    }

    suspend fun markStep2Complete(): Unit = withContext(Dispatchers.IO) {
        ctx.onboardingStore.edit { prefs ->
            prefs[keyStep2Complete] = true
        }
    }

    // ── Migration ────────────────────────────────────────────────────────────

    /**
     * One-time migration for users who had the app before the wizard was introduced.
     *
     * If the user has all required settings already configured (URL + token + printers +
     * mappings), we advance them to step 3 and mark steps 1 and 2 as complete, so they
     * only need to confirm static IP and test print.
     *
     * This does NOT call [setComplete] — even migrated users must confirm steps 3 and 4.
     *
     * Safe to call on every app start: it no-ops when [keyMigrationDone] is already true
     * or when setup is already complete.
     */
    suspend fun runMigrationIfNeeded(
        appSettings: AppSettings,
        tokenStorage: SecureTokenStorage,
        printerRepo: PrinterRepository,
        mappingRepo: PrinterMappingRepository,
    ): Unit = withContext(Dispatchers.IO) {
        val prefs = ctx.onboardingStore.data.first()

        // Already migrated or already complete — nothing to do
        if (prefs[keyMigrationDone] == true || prefs[keySetupComplete] == true) return@withContext

        val hasUrl = try { appSettings.getBackendUrl()?.isNotBlank() == true } catch (_: Exception) { false }
        val hasToken = try { tokenStorage.hasToken() } catch (_: Exception) { false }
        val hasPrinters = try { printerRepo.loadAll().isNotEmpty() } catch (_: Exception) { false }
        val hasMappings = try { mappingRepo.loadAll().isNotEmpty() } catch (_: Exception) { false }

        if (hasUrl && hasToken && hasPrinters && hasMappings) {
            // Existing configured installation — skip to step 3
            ctx.onboardingStore.edit { p ->
                p[keyWizardStep]    = 3
                p[keyStep1Complete] = true
                p[keyStep2Complete] = true
                p[keyMigrationDone] = true
            }
        } else {
            // Fresh or incomplete installation — start from step 1
            ctx.onboardingStore.edit { p ->
                p[keyWizardStep]    = 1
                p[keyMigrationDone] = true
            }
        }
    }
}
