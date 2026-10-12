package com.commu.printeragent.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around OkHttp for backend API calls.
 * All calls are dispatched on Dispatchers.IO.
 * Authorization headers and tokens are never logged.
 */
class BackendApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Tests connectivity and authentication by calling GET /printers/tenant/{tenantId}/active.
     * Returns success on HTTP 200, a typed failure otherwise.
     *
     * The URL is constructed with HttpUrl path segments so that tenantId cannot inject
     * extra path components regardless of its content.
     */
    suspend fun testConnection(
        baseUrl: String,
        tenantId: String,
        token: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val parsedBase = baseUrl.toHttpUrlOrNull()
                ?: return@withContext Result.failure(Exception("פורמט כתובת שגוי"))

            val url = parsedBase.newBuilder()
                .addPathSegments("printers/tenant")
                .addPathSegment(tenantId)
                .addPathSegment("active")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> Result.success(Unit)
                    401 -> Result.failure(Exception("שגיאת אימות (401) — בדוק את אסימון הסוכן"))
                    403 -> Result.failure(Exception("אין הרשאה (403) — בדוק מזהה דייר והרשאות"))
                    404 -> Result.failure(Exception("נתיב לא נמצא (404) — בדוק כתובת שרת ומזהה דייר"))
                    else -> Result.failure(Exception("שרת החזיר HTTP ${response.code}"))
                }
            }
        } catch (e: ConnectException) {
            Result.failure(Exception("לא ניתן להתחבר לשרת — בדוק כתובת ורשת"))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("תם הזמן — השרת אינו מגיב"))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("לא ניתן לפתור כתובת שרת — בדוק URL"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "שגיאה לא ידועה"))
        }
    }

    // ── Agent Sync ────────────────────────────────────────────────────────────

    /**
     * POST /printers/tenant/{tenantId}/agent-sync
     *
     * Registers the given printer names with the backend for this tenant.
     * Accepts HTTP 200 or 204 as success; any other status is a failure.
     */
    suspend fun agentSync(
        baseUrl: String,
        tenantId: String,
        token: String,
        activePrinterNames: List<String>,
        mainPrinterName: String? = null,
        cashDrawerPrinterNames: List<String> = emptyList(),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val parsedBase = baseUrl.toHttpUrlOrNull()
                ?: return@withContext Result.failure(Exception("פורמט כתובת שגוי"))

            val url = parsedBase.newBuilder()
                .addPathSegments("printers/tenant")
                .addPathSegment(tenantId)
                .addPathSegment("agent-sync")
                .build()

            val body = buildSyncBody(activePrinterNames, mainPrinterName, cashDrawerPrinterNames)
            val requestBody = body.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200, 201, 204 -> Result.success(Unit)
                    401 -> Result.failure(Exception("שגיאת אימות (401) — בדוק את אסימון הסוכן"))
                    403 -> Result.failure(Exception("אין הרשאה (403) — בדוק מזהה דייר"))
                    404 -> Result.failure(Exception("נתיב לא נמצא (404) — בדוק כתובת שרת"))
                    409 -> Result.failure(Exception("קונפליקט שמות (409) — בדוק כפילויות"))
                    else -> Result.failure(Exception("שרת החזיר HTTP ${response.code}"))
                }
            }
        } catch (e: ConnectException) {
            Result.failure(Exception("לא ניתן להתחבר לשרת — בדוק כתובת ורשת"))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("תם הזמן — השרת אינו מגיב"))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("לא ניתן לפתור כתובת שרת"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "שגיאה לא ידועה"))
        }
    }

    // ── Fetch Active Printers ─────────────────────────────────────────────────

    /**
     * GET /printers/tenant/{tenantId}/active
     *
     * Returns the backend printer list for this tenant. Each entry has at least
     * an integer [id] and a string [name]. Unknown fields are ignored.
     */
    suspend fun fetchActivePrinters(
        baseUrl: String,
        tenantId: String,
        token: String,
    ): Result<List<BackendPrinter>> = withContext(Dispatchers.IO) {
        try {
            val parsedBase = baseUrl.toHttpUrlOrNull()
                ?: return@withContext Result.failure(Exception("פורמט כתובת שגוי"))

            val url = parsedBase.newBuilder()
                .addPathSegments("printers/tenant")
                .addPathSegment(tenantId)
                .addPathSegment("active")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val bodyStr = response.body?.string() ?: "[]"
                        Result.success(parseBackendPrinters(bodyStr))
                    }
                    401 -> Result.failure(Exception("שגיאת אימות (401)"))
                    403 -> Result.failure(Exception("אין הרשאה (403)"))
                    404 -> Result.failure(Exception("נתיב לא נמצא (404)"))
                    else -> Result.failure(Exception("שרת החזיר HTTP ${response.code}"))
                }
            }
        } catch (e: ConnectException) {
            Result.failure(Exception("לא ניתן להתחבר לשרת"))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("תם הזמן"))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("לא ניתן לפתור כתובת שרת"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "שגיאה לא ידועה"))
        }
    }

    // ── Job Queue ─────────────────────────────────────────────────────────────

    /**
     * GET /printers/tenant/{tenantId}/print-jobs/agent-queue?printerIds=1,2&limit=25
     *
     * Returns queued jobs (oldest first) for the given backend printer IDs.
     * An empty list means there is no work to do.
     */
    suspend fun fetchJobQueue(
        baseUrl: String,
        tenantId: String,
        token: String,
        printerIds: List<Int>,
        limit: Int = 25,
    ): Result<List<PrintJob>> = withContext(Dispatchers.IO) {
        try {
            val parsedBase = baseUrl.toHttpUrlOrNull()
                ?: return@withContext Result.failure(Exception("פורמט כתובת שגוי"))

            val urlBuilder = parsedBase.newBuilder()
                .addPathSegments("printers/tenant")
                .addPathSegment(tenantId)
                .addPathSegments("print-jobs/agent-queue")
                .addQueryParameter("limit", limit.toString())
            if (printerIds.isNotEmpty()) {
                urlBuilder.addQueryParameter("printerIds", printerIds.joinToString(","))
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> Result.success(parseJobs(response.body?.string() ?: "[]"))
                    401 -> Result.failure(Exception("שגיאת אימות (401) — בדוק את אסימון הסוכן"))
                    403 -> Result.failure(Exception("אין הרשאה (403) — בדוק מזהה דייר"))
                    404 -> Result.failure(Exception("נתיב לא נמצא (404) — בדוק כתובת שרת"))
                    else -> Result.failure(Exception("שרת החזיר HTTP ${response.code}"))
                }
            }
        } catch (e: ConnectException) {
            Result.failure(Exception("לא ניתן להתחבר לשרת — בדוק כתובת ורשת"))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("תם הזמן — השרת אינו מגיב"))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("לא ניתן לפתור כתובת שרת — בדוק URL"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "שגיאה לא ידועה"))
        }
    }

    /**
     * PATCH /printers/tenant/{tenantId}/print-jobs/{jobId}/status
     *
     * Updates a job's status. For "failed", [errorMessage] is sent to the backend (max 500 chars).
     * Returns success on HTTP 200; the response body is not parsed — callers only need
     * confirmation that the server accepted the transition.
     */
    suspend fun patchJobStatus(
        baseUrl: String,
        tenantId: String,
        token: String,
        jobId: Int,
        status: String,
        errorMessage: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val parsedBase = baseUrl.toHttpUrlOrNull()
                ?: return@withContext Result.failure(Exception("פורמט כתובת שגוי"))

            val url = parsedBase.newBuilder()
                .addPathSegments("printers/tenant")
                .addPathSegment(tenantId)
                .addPathSegments("print-jobs")
                .addPathSegment(jobId.toString())
                .addPathSegment("status")
                .build()

            val body = buildPatchStatusBody(status, errorMessage)
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .patch(body)
                .build()

            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> Result.success(Unit)
                    400 -> Result.failure(Exception("בקשה לא תקינה (400)"))
                    401 -> Result.failure(Exception("שגיאת אימות (401)"))
                    404 -> Result.failure(Exception("עבודה לא נמצאת (404)"))
                    else -> Result.failure(Exception("שרת החזיר HTTP ${response.code}"))
                }
            }
        } catch (e: ConnectException) {
            Result.failure(Exception("לא ניתן להתחבר לשרת"))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("תם הזמן"))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("לא ניתן לפתור כתובת שרת"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "שגיאה לא ידועה"))
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Parses a JSON array of printer objects. Skips entries with missing or invalid id/name. */
    internal fun parseBackendPrinters(json: String): List<BackendPrinter> = try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optInt("id", -1).takeIf { it >= 0 } ?: return@mapNotNull null
            val name = obj.optString("name").trim().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            BackendPrinter(id = id, name = name)
        }
    } catch (_: JSONException) {
        emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Builds the agent-sync request body as a JSON string.
     * Uses manual construction to avoid adding a JSON library dependency at runtime.
     */
    private fun buildSyncBody(
        activePrinterNames: List<String>,
        mainPrinterName: String?,
        cashDrawerPrinterNames: List<String>,
    ): String {
        val names = activePrinterNames.joinToString(",") { "\"${it.escapeJson()}\"" }
        val cashDrawer = cashDrawerPrinterNames.joinToString(",") { "\"${it.escapeJson()}\"" }
        val mainPart = if (mainPrinterName != null)
            ",\"mainPrinterName\":\"${mainPrinterName.escapeJson()}\""
        else ""
        return "{\"activePrinterNames\":[$names],\"cashDrawerPrinterNames\":[$cashDrawer]$mainPart}"
    }

    /**
     * Parses a JSON array of print job objects.
     * Skips entries where id or printerId is missing/invalid.
     */
    internal fun parseJobs(json: String): List<PrintJob> = try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optInt("id", -1).takeIf { it >= 0 } ?: return@mapNotNull null
            val printerId = obj.optInt("printerId", -1).takeIf { it >= 0 } ?: return@mapNotNull null
            PrintJob(
                id = id,
                printerId = printerId,
                printerName = obj.optString("printerName", ""),
                status = obj.optString("status", "queued"),
                printableText = obj.optString("printableText", ""),
                errorMessage = obj.optJobString("errorMessage"),
            )
        }
    } catch (_: JSONException) {
        emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun buildPatchStatusBody(status: String, errorMessage: String?): String {
        val sb = StringBuilder("{\"status\":\"${status.escapeJson()}\"")
        if (errorMessage != null) {
            sb.append(",\"errorMessage\":\"${errorMessage.take(500).escapeJson()}\"")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")
}

// Returns null if the key is absent, JSON null, or blank; avoids using optString(key, null)
// whose behaviour across org.json versions is inconsistent.
private fun JSONObject.optJobString(key: String): String? =
    if (has(key) && !isNull(key)) getString(key).takeIf { it.isNotBlank() } else null
