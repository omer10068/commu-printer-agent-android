package com.commu.printeragent.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.commu.printeragent.data.AgentPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restarts the print-agent foreground service after BOOT_COMPLETED if the user had
 * the agent enabled before the reboot.
 *
 * Caveats:
 *   - Only [AgentPreferences] (plain DataStore) is read here — it is always available
 *     after boot.  The encrypted agent token (EncryptedSharedPreferences) is stored in
 *     credential-encrypted storage and is NOT readable until the device is unlocked for
 *     the first time.  [PrintAgentService.loadConfig] handles this gracefully: it catches
 *     the Keystore exception and returns null, keeping the loop in [AgentStatus.NoConfig]
 *     until the device is unlocked, at which point it recovers without user action.
 *   - BOOT_COMPLETED receivers are explicitly permitted to start foreground services on
 *     all Android versions this app targets.
 *   - android:exported="false" prevents third-party apps from sending BOOT_COMPLETED to
 *     this receiver; the system broadcast is still delivered regardless.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (AgentPreferences(context).isEnabled()) {
                    ContextCompat.startForegroundService(
                        context,
                        PrintAgentService.startIntent(context),
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
