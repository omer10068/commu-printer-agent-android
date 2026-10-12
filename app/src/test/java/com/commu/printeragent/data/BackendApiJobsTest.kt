package com.commu.printeragent.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackendApiJobsTest {

    private lateinit var server: MockWebServer
    private lateinit var api: BackendApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = BackendApi()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── fetchJobQueue — happy path ────────────────────────────────────────────

    @Test
    fun `fetchJobQueue HTTP 200 empty array returns empty list`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val result = api.fetchJobQueue(baseUrl(), "demo", "pa_test", listOf(1, 2))
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `fetchJobQueue HTTP 200 two jobs parses correctly`() = runTest {
        val json = """[
            {"id":5,"printerId":1,"printerName":"Kitchen","status":"queued","printableText":"Hello","errorMessage":null},
            {"id":6,"printerId":2,"printerName":"Bar","status":"queued","printableText":"World","errorMessage":null}
        ]"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))
        val jobs = api.fetchJobQueue(baseUrl(), "demo", "pa_test", listOf(1, 2)).getOrThrow()
        assertEquals(2, jobs.size)
        assertEquals(5, jobs[0].id)
        assertEquals(1, jobs[0].printerId)
        assertEquals("Kitchen", jobs[0].printerName)
        assertEquals("Hello", jobs[0].printableText)
        assertEquals(6, jobs[1].id)
    }

    @Test
    fun `fetchJobQueue HTTP 401 returns failure with 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = api.fetchJobQueue(baseUrl(), "demo", "pa_bad", listOf(1))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }

    @Test
    fun `fetchJobQueue HTTP 403 returns failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        val result = api.fetchJobQueue(baseUrl(), "demo", "pa_test", listOf(1))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("403") == true)
    }

    @Test
    fun `fetchJobQueue HTTP 500 returns failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = api.fetchJobQueue(baseUrl(), "demo", "pa_test", listOf(1))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }

    // ── fetchJobQueue — request shape ─────────────────────────────────────────

    @Test
    fun `fetchJobQueue path is print-jobs-agent-queue`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        api.fetchJobQueue(baseUrl(), "my-tenant", "pa_test", listOf(1, 2))
        val request = server.takeRequest()
        assertTrue(request.path?.contains("/printers/tenant/my-tenant/print-jobs/agent-queue") == true)
    }

    @Test
    fun `fetchJobQueue includes printerIds query parameter`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        api.fetchJobQueue(baseUrl(), "demo", "pa_test", listOf(1, 2))
        val request = server.takeRequest()
        val path = request.path ?: ""
        assertTrue(path.contains("printerIds=1%2C2") || path.contains("printerIds=1,2"))
    }

    @Test
    fun `fetchJobQueue includes limit query parameter`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        api.fetchJobQueue(baseUrl(), "demo", "pa_test", listOf(1), limit = 10)
        val request = server.takeRequest()
        assertTrue(request.path?.contains("limit=10") == true)
    }

    @Test
    fun `fetchJobQueue sends Authorization header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        api.fetchJobQueue(baseUrl(), "demo", "pa_xyz", listOf(1))
        val request = server.takeRequest()
        assertEquals("Bearer pa_xyz", request.getHeader("Authorization"))
    }

    @Test
    fun `fetchJobQueue uses GET method`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        api.fetchJobQueue(baseUrl(), "demo", "pa_test", listOf(1))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
    }

    // ── fetchJobQueue — server unreachable ────────────────────────────────────

    @Test
    fun `fetchJobQueue unreachable server returns failure`() = runTest {
        val url = baseUrl()
        server.shutdown()
        val result = api.fetchJobQueue(url, "demo", "pa_test", listOf(1))
        assertTrue(result.isFailure)
    }

    // ── patchJobStatus — happy path ───────────────────────────────────────────

    @Test
    fun `patchJobStatus HTTP 200 claimed returns success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(jobJson(7, "claimed")))
        val result = api.patchJobStatus(baseUrl(), "demo", "pa_test", 7, "claimed")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `patchJobStatus HTTP 200 printed returns success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(jobJson(7, "printed")))
        val result = api.patchJobStatus(baseUrl(), "demo", "pa_test", 7, "printed")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `patchJobStatus HTTP 200 failed with errorMessage returns success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(jobJson(7, "failed")))
        val result = api.patchJobStatus(baseUrl(), "demo", "pa_test", 7, "failed", "printer offline")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `patchJobStatus HTTP 400 returns failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400))
        val result = api.patchJobStatus(baseUrl(), "demo", "pa_test", 7, "badstatus")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("400") == true)
    }

    @Test
    fun `patchJobStatus HTTP 401 returns failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = api.patchJobStatus(baseUrl(), "demo", "pa_bad", 7, "claimed")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }

    @Test
    fun `patchJobStatus HTTP 404 returns failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = api.patchJobStatus(baseUrl(), "demo", "pa_test", 999, "claimed")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("404") == true)
    }

    // ── patchJobStatus — request shape ────────────────────────────────────────

    @Test
    fun `patchJobStatus path includes jobId and status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(jobJson(42, "claimed")))
        api.patchJobStatus(baseUrl(), "my-tenant", "pa_test", 42, "claimed")
        val request = server.takeRequest()
        assertEquals("/printers/tenant/my-tenant/print-jobs/42/status", request.path)
    }

    @Test
    fun `patchJobStatus uses PATCH method`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(jobJson(7, "claimed")))
        api.patchJobStatus(baseUrl(), "demo", "pa_test", 7, "claimed")
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
    }

    @Test
    fun `patchJobStatus sends Authorization header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(jobJson(7, "printed")))
        api.patchJobStatus(baseUrl(), "demo", "pa_abc", 7, "printed")
        val request = server.takeRequest()
        assertEquals("Bearer pa_abc", request.getHeader("Authorization"))
    }

    @Test
    fun `patchJobStatus body contains status field`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(jobJson(7, "claimed")))
        api.patchJobStatus(baseUrl(), "demo", "pa_test", 7, "claimed")
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"status\""))
        assertTrue(body.contains("claimed"))
    }

    @Test
    fun `patchJobStatus body contains errorMessage when status is failed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(jobJson(7, "failed")))
        api.patchJobStatus(baseUrl(), "demo", "pa_test", 7, "failed", "printer offline")
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"errorMessage\""))
        assertTrue(body.contains("printer offline"))
    }

    @Test
    fun `patchJobStatus body does not contain errorMessage when null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(jobJson(7, "printed")))
        api.patchJobStatus(baseUrl(), "demo", "pa_test", 7, "printed", null)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(!body.contains("errorMessage"))
    }

    // ── parseJobs — unit tests for internal parser ────────────────────────────

    @Test
    fun `parseJobs parses id, printerId, printerName, status, printableText`() {
        val json = """[{"id":3,"printerId":2,"printerName":"Bar","status":"queued","printableText":"Hello\nWorld"}]"""
        val jobs = api.parseJobs(json)
        assertEquals(1, jobs.size)
        assertEquals(3, jobs[0].id)
        assertEquals(2, jobs[0].printerId)
        assertEquals("Bar", jobs[0].printerName)
        assertEquals("queued", jobs[0].status)
        assertEquals("Hello\nWorld", jobs[0].printableText)
    }

    @Test
    fun `parseJobs sets errorMessage to null when JSON null`() {
        val json = """[{"id":1,"printerId":1,"printerName":"K","status":"queued","printableText":"t","errorMessage":null}]"""
        val jobs = api.parseJobs(json)
        assertEquals(1, jobs.size)
        assertNull(jobs[0].errorMessage)
    }

    @Test
    fun `parseJobs captures errorMessage for failed jobs`() {
        val json = """[{"id":1,"printerId":1,"printerName":"K","status":"failed","printableText":"t","errorMessage":"printer offline"}]"""
        val jobs = api.parseJobs(json)
        assertEquals("printer offline", jobs[0].errorMessage)
    }

    @Test
    fun `parseJobs skips entry with missing id`() {
        val json = """[{"printerId":1,"printerName":"K","status":"queued","printableText":"t"},{"id":2,"printerId":1,"printerName":"K","status":"queued","printableText":"ok"}]"""
        val jobs = api.parseJobs(json)
        assertEquals(1, jobs.size)
        assertEquals(2, jobs[0].id)
    }

    @Test
    fun `parseJobs skips entry with missing printerId`() {
        val json = """[{"id":1,"printerName":"K","status":"queued","printableText":"t"},{"id":2,"printerId":2,"printerName":"K","status":"queued","printableText":"ok"}]"""
        val jobs = api.parseJobs(json)
        assertEquals(1, jobs.size)
        assertEquals(2, jobs[0].id)
    }

    @Test
    fun `parseJobs returns empty list for empty array`() {
        assertTrue(api.parseJobs("[]").isEmpty())
    }

    @Test
    fun `parseJobs returns empty list for malformed JSON`() {
        assertTrue(api.parseJobs("not-json").isEmpty())
    }

    @Test
    fun `parseJobs handles Hebrew printableText without corruption`() {
        // JSON newlines must be escaped as \n; raw newline inside a JSON string is invalid.
        val json = """[{"id":1,"printerId":1,"printerName":"K","status":"queued","printableText":"*** בדיקת הדפסה ***\nמסעדת הדגמה"}]"""
        val jobs = api.parseJobs(json)
        assertEquals(1, jobs.size)
        assertTrue(jobs[0].printableText.isNotBlank())
        assertTrue(jobs[0].printableText.contains("בדיקת"))  // "בדיקת"
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    private fun jobJson(id: Int, status: String): String =
        """{"id":$id,"printerId":1,"printerName":"Kitchen","status":"$status","printableText":"test","errorMessage":null}"""
}
