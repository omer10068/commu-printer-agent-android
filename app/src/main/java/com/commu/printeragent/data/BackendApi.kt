package com.commu.printeragent.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
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
}
