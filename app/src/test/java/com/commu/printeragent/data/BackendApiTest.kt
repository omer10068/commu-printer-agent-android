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

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Returns the MockWebServer base URL without a trailing slash. */
    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')
}
