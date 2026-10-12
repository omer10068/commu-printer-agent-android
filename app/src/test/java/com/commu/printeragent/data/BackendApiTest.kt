package com.commu.printeragent.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackendApiTest {

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

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    fun `HTTP 200 returns success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val result = api.testConnection(baseUrl(), "demo", "pa_test")
        assertTrue(result.isSuccess)
    }

    // ── Auth / permission errors ─────────────────────────────────────────────

    @Test
    fun `HTTP 401 returns failure mentioning 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = api.testConnection(baseUrl(), "demo", "pa_bad")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }

    @Test
    fun `HTTP 403 returns failure mentioning 403`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        val result = api.testConnection(baseUrl(), "demo", "pa_test")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("403") == true)
    }

    @Test
    fun `HTTP 404 returns failure mentioning 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = api.testConnection(baseUrl(), "wrong-tenant", "pa_test")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("404") == true)
    }

    @Test
    fun `unexpected HTTP status returns failure mentioning the code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = api.testConnection(baseUrl(), "demo", "pa_test")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }

    // ── Network failures ─────────────────────────────────────────────────────

    @Test
    fun `unreachable server returns failure`() = runTest {
        val url = baseUrl()
        server.shutdown() // shut down before the request is made
        val result = api.testConnection(url, "demo", "pa_test")
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull()?.message)
    }

    @Test
    fun `invalid URL returns failure`() = runTest {
        val result = api.testConnection("not-a-url", "demo", "pa_test")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("פורמט") == true)
    }

    @Test
    fun `blank URL returns failure`() = runTest {
        val result = api.testConnection("", "demo", "pa_test")
        assertTrue(result.isFailure)
    }

    // ── Request shape ────────────────────────────────────────────────────────

    @Test
    fun `request path is printers-tenant-tenantId-active`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        api.testConnection(baseUrl(), "my-tenant", "pa_test")
        val request = server.takeRequest()
        assertEquals("/printers/tenant/my-tenant/active", request.path)
    }

    @Test
    fun `Authorization header is Bearer token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        api.testConnection(baseUrl(), "demo", "pa_abc123")
        val request = server.takeRequest()
        assertEquals("Bearer pa_abc123", request.getHeader("Authorization"))
    }

    @Test
    fun `tenantId special characters are percent-encoded in path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        api.testConnection(baseUrl(), "tenant/slash", "pa_test")
        val request = server.takeRequest()
        assertEquals("/printers/tenant/tenant%2Fslash/active", request.path)
    }

    @Test
    fun `non-http scheme returns failure with format message`() = runTest {
        val result = api.testConnection("ftp://server:5100", "demo", "pa_test")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("פורמט") == true)
    }

    // ── agentSync — happy path ────────────────────────────────────────────────

    @Test
    fun `agentSync HTTP 200 returns success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val result = api.agentSync(baseUrl(), "demo", "pa_test", listOf("Kitchen"), "Kitchen")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `agentSync HTTP 204 returns success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val result = api.agentSync(baseUrl(), "demo", "pa_test", listOf("Kitchen"))
        assertTrue(result.isSuccess)
    }

    @Test
    fun `agentSync HTTP 401 returns failure with 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = api.agentSync(baseUrl(), "demo", "pa_bad", listOf("Kitchen"))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }

    @Test
    fun `agentSync request path is printers-tenant-tenantId-agent-sync`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.agentSync(baseUrl(), "my-tenant", "pa_test", listOf("Kitchen"))
        val request = server.takeRequest()
        assertEquals("/printers/tenant/my-tenant/agent-sync", request.path)
    }

    @Test
    fun `agentSync uses POST method`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.agentSync(baseUrl(), "demo", "pa_test", listOf("Kitchen"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
    }

    @Test
    fun `agentSync sends Authorization header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.agentSync(baseUrl(), "demo", "pa_xyz", listOf("Kitchen"))
        val request = server.takeRequest()
        assertEquals("Bearer pa_xyz", request.getHeader("Authorization"))
    }

    @Test
    fun `agentSync body contains activePrinterNames`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.agentSync(baseUrl(), "demo", "pa_test", listOf("Kitchen", "Bar"))
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("activePrinterNames"))
        assertTrue(body.contains("Kitchen"))
        assertTrue(body.contains("Bar"))
    }

    @Test
    fun `agentSync body contains mainPrinterName when provided`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.agentSync(baseUrl(), "demo", "pa_test", listOf("Kitchen", "Bar"), "Kitchen")
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("mainPrinterName"))
        assertTrue(body.contains("Kitchen"))
    }

    // ── fetchActivePrinters ───────────────────────────────────────────────────

    @Test
    fun `fetchActivePrinters HTTP 200 with empty array returns empty list`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val result = api.fetchActivePrinters(baseUrl(), "demo", "pa_test")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `fetchActivePrinters parses two printer objects`() = runTest {
        val json = """[{"id":1,"name":"Kitchen"},{"id":2,"name":"Bar"}]"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))
        val result = api.fetchActivePrinters(baseUrl(), "demo", "pa_test")
        assertTrue(result.isSuccess)
        val printers = result.getOrThrow()
        assertEquals(2, printers.size)
        assertEquals(1, printers[0].id)
        assertEquals("Kitchen", printers[0].name)
        assertEquals(2, printers[1].id)
        assertEquals("Bar", printers[1].name)
    }

    @Test
    fun `fetchActivePrinters HTTP 401 returns failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = api.fetchActivePrinters(baseUrl(), "demo", "pa_bad")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }

    @Test
    fun `fetchActivePrinters request path is printers-tenant-tenantId-active`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        api.fetchActivePrinters(baseUrl(), "my-tenant", "pa_test")
        val request = server.takeRequest()
        assertEquals("/printers/tenant/my-tenant/active", request.path)
    }

    // ── parseBackendPrinters (internal, unit-tested directly) ─────────────────

    @Test
    fun `parseBackendPrinters parses id and name fields`() {
        val json = """[{"id":5,"name":"Main"}]"""
        val result = api.parseBackendPrinters(json)
        assertEquals(1, result.size)
        assertEquals(5, result[0].id)
        assertEquals("Main", result[0].name)
    }

    @Test
    fun `parseBackendPrinters skips entry with missing id`() {
        val json = """[{"name":"Orphan"},{"id":2,"name":"Valid"}]"""
        val result = api.parseBackendPrinters(json)
        assertEquals(1, result.size)
        assertEquals(2, result[0].id)
    }

    @Test
    fun `parseBackendPrinters skips entry with blank name`() {
        val json = """[{"id":1,"name":""},{"id":2,"name":"Valid"}]"""
        val result = api.parseBackendPrinters(json)
        assertEquals(1, result.size)
        assertEquals(2, result[0].id)
    }

    @Test
    fun `parseBackendPrinters returns empty list for invalid JSON`() {
        val result = api.parseBackendPrinters("not-json")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseBackendPrinters returns empty list for empty array`() {
        val result = api.parseBackendPrinters("[]")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseBackendPrinters ignores unknown extra fields`() {
        val json = """[{"id":1,"name":"Kitchen","isActive":true,"tenantId":"demo"}]"""
        val result = api.parseBackendPrinters(json)
        assertEquals(1, result.size)
        assertEquals(1, result[0].id)
        assertEquals("Kitchen", result[0].name)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Returns the MockWebServer base URL without a trailing slash. */
    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')
}
