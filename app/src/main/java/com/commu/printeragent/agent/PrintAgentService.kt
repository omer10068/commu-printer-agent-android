package com.commu.printeragent.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.commu.printeragent.R
import com.commu.printeragent.data.AgentPreferences
import com.commu.printeragent.data.BackendApi
import com.commu.printeragent.data.AppSettings
import com.commu.printeragent.data.PendingPatch
import com.commu.printeragent.data.PendingStatusRepository
import com.commu.printeragent.data.PrinterMappingRepository
import com.commu.printeragent.data.PrinterRepository
import com.commu.printeragent.data.SecureTokenStorage
import com.commu.printeragent.printing.EscPosEncoder
import com.commu.printeragent.printing.PrinterChannel
import com.commu.printeragent.printing.ReceiptRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that runs the automatic print-job polling loop.
 *
 * Foreground-service type: connectedDevice — the agent communicates continuously
 * with LAN-connected ESC/POS thermal printers via TCP (port 9100).
 *
 * Required permissions declared in AndroidManifest.xml:
 *   - FOREGROUND_SERVICE (all API levels)
 *   - FOREGROUND_SERVICE_CONNECTED_DEVICE (API 34+)
 *   - CHANGE_NETWORK_STATE (associated permission for connectedDevice type)
 *   - POST_NOTIFICATIONS (API 33+, for visible notification)
 *   - RECEIVE_BOOT_COMPLETED (for [BootReceiver])
 *
 * Lifecycle:
 *   Start  → send ACTION_DEFAULT (null) to this service via [startIntent]
 *   Stop   → send ACTION_STOP via [stopIntent], or user taps "עצור" in the notification
 *
 * The encrypted agent token (EncryptedSharedPreferences) is unavailable before the first
 * device unlock after a reboot.  The polling loop handles this gracefully: if credentials
 * are missing or unreadable, [AgentStatus.NoConfig] is emitted and the loop retries without
 * crashing.  No credentials or tokens are ever written to logcat.
 */
class PrintAgentService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val appSettings by lazy { AppSettings(this) }
    private val tokenStorage by lazy { SecureTokenStorage(this) }
    private val mappingRepo by lazy { PrinterMappingRepository(this) }
    private val printerRepo by lazy { PrinterRepository(this) }
    private val pendingRepo by lazy { PendingStatusRepository(this) }
    private val agentPrefs by lazy { AgentPreferences(this) }
    private val api by lazy { BackendApi() }

    private var loop: PrintAgentLoop? = null

    // ── Android lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopAgent(startId)
            else -> startAgent()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        AgentState.markStopped()
    }

    // ── Start / stop ──────────────────────────────────────────────────────────

    private fun startAgent() {
        AgentState.setStatus(AgentStatus.Starting)

        val notification = buildNotification("מתחיל…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        scope.launch(NonCancellable) { agentPrefs.setEnabled(true) }

        if (loop?.isActive == true) return

        val newLoop = PrintAgentLoop(
            loadConfig = ::loadConfig,
            runCycle = ::runCycle,
            onStatus = { status ->
                AgentState.setStatus(status)
                updateNotification(status)
            },
            processingMutex = AgentState.processingMutex,
        )
        loop = newLoop
        newLoop.start(scope)
    }

    private fun stopAgent(startId: Int) {
        loop?.stop()
        loop = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Persist disabled state before destroying the service. Calling stopSelf(startId)
        // only after the DataStore suspend completes ensures onDestroy() cannot run until
        // the preference is on disk. stopSelf(startId) is a no-op if a newer onStartCommand
        // has already arrived (rapid Stop → Start), so it cannot kill a restarted agent.
        // NonCancellable is defence-in-depth against scope.cancel() racing the write.
        // Process kill between stopForeground and stopSelf is a platform-level risk.
        scope.launch(NonCancellable) {
            agentPrefs.setEnabled(false)
            stopSelf(startId)
        }
    }

    // ── Polling logic ─────────────────────────────────────────────────────────

    private suspend fun loadConfig(): PrintAgentLoop.LoopConfig? {
        val url = appSettings.getBackendUrl()?.takeIf { it.isNotBlank() } ?: return null
        val tenantId = appSettings.getTenantId()?.takeIf { it.isNotBlank() } ?: return null
        val token = try {
            tokenStorage.getToken()?.takeIf { it.isNotBlank() } ?: return null
        } catch (_: Exception) {
            return null
        }
        val mappings = mappingRepo.loadAll()
        if (mappings.isEmpty()) return null
        val printers = printerRepo.loadAll()
        return PrintAgentLoop.LoopConfig(url, tenantId, token, mappings, printers)
    }

    private suspend fun runCycle(
        config: PrintAgentLoop.LoopConfig,
        @Suppress("UNUSED_PARAMETER") onProgress: (String) -> Unit,
    ): PrintAgentLoop.CycleResult {
        // Retry any PATCH calls that failed after successful TCP sends in previous cycles.
        drainPendingPatches(config)

        val processor = JobProcessor(
            fetchQueue = { url, tid, tok, ids -> api.fetchJobQueue(url, tid, tok, ids) },
            patchStatus = { url, tid, tok, jid, status, err ->
                api.patchJobStatus(url, tid, tok, jid, status, err)
            },
            send = { addr, port, bytes -> PrinterChannel.send(addr, port, bytes) },
            render = { text ->
                withContext(Dispatchers.IO) {
                    EscPosEncoder.buildRasterReceipt(ReceiptRenderer.renderText(text))
                }
            },
        )

        val results = processor.processQueue(
            baseUrl = config.baseUrl,
            tenantId = config.tenantId,
            token = config.token,
            mappings = config.mappings,
            printers = config.printers,
        )

        // Save any PATCH failures for retry. Never re-send ESC/POS bytes.
        for (result in results) {
            if (result.isSuccess && !result.patchReported) {
                pendingRepo.add(PendingPatch(result.jobId, "printed", null))
            }
        }

        val printed = results.count { it.isSuccess }
        val failed = results.count { !it.isSuccess }
        AgentState.recordCycle(System.currentTimeMillis(), printed, failed)

        return if (results.isEmpty()) PrintAgentLoop.CycleResult.Empty
        else PrintAgentLoop.CycleResult.Processed(printed, failed)
    }

    /**
     * Attempts to re-send all pending PATCH calls from previous cycles where TCP
     * succeeded but the backend report was not confirmed.  Successfully retried
     * entries are removed from persistence.
     */
    private suspend fun drainPendingPatches(config: PrintAgentLoop.LoopConfig) {
        for (patch in pendingRepo.loadAll()) {
            val result = api.patchJobStatus(
                config.baseUrl, config.tenantId, config.token,
                patch.jobId, patch.status, patch.errorMessage,
            )
            if (result.isSuccess) pendingRepo.remove(patch.jobId)
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun updateNotification(status: AgentStatus) {
        val text = when (status) {
            AgentStatus.Polling -> "בודק תור הדפסה…"
            is AgentStatus.Idle -> "ממתין לעבודות חדשות"
            is AgentStatus.BackingOff -> "שגיאת חיבור — ניסיון חוזר בקרוב"
            AgentStatus.NoConfig -> "הגדרות חסרות — בדוק חיבור ומדפסות"
            AgentStatus.Starting -> "מתחיל…"
            AgentStatus.Stopped -> return
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(contentText: String): Notification {
        val stopPendingIntent = PendingIntent.getService(
            this, 0,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_print)
            .setContentTitle("Commu Print Agent")
            .setContentText(contentText)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "עצור", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Commu Print Agent",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "סוכן הדפסה — מעבד תורי הדפסה ברקע"
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        const val ACTION_STOP = "com.commu.printeragent.STOP_AGENT"
        const val CHANNEL_ID = "print_agent_channel"
        const val NOTIFICATION_ID = 1001

        fun startIntent(context: Context): Intent =
            Intent(context, PrintAgentService::class.java)

        fun stopIntent(context: Context): Intent =
            Intent(context, PrintAgentService::class.java).apply { action = ACTION_STOP }
    }
}
