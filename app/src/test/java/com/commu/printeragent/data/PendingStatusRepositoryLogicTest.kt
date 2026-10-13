package com.commu.printeragent.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PendingStatusRepository.encode] and [PendingStatusRepository.decode].
 *
 * These are pure functions with no Android or DataStore dependency — plain JVM tests.
 */
class PendingStatusRepositoryLogicTest {

    @Test
    fun `encode empty list produces empty JSON array`() {
        assertEquals("[]", PendingStatusRepository.encode(emptyList()))
    }

    @Test
    fun `round-trip preserves all fields`() {
        val patches = listOf(
            PendingPatch(1, "printed", null),
            PendingPatch(2, "failed", "connection refused"),
        )
        val decoded = PendingStatusRepository.decode(PendingStatusRepository.encode(patches))
        assertEquals(patches, decoded)
    }

    @Test
    fun `decode empty string returns empty list`() {
        assertTrue(PendingStatusRepository.decode("").isEmpty())
    }

    @Test
    fun `decode blank string returns empty list`() {
        assertTrue(PendingStatusRepository.decode("   ").isEmpty())
    }

    @Test
    fun `decode malformed JSON returns empty list`() {
        assertTrue(PendingStatusRepository.decode("{not valid json}").isEmpty())
    }

    @Test
    fun `null errorMessage survives encode-decode as null`() {
        val patch = PendingPatch(42, "printed", null)
        val decoded = PendingStatusRepository.decode(PendingStatusRepository.encode(listOf(patch)))
        assertEquals(1, decoded.size)
        assertNull(decoded[0].errorMessage)
    }

    @Test
    fun `non-null errorMessage survives encode-decode`() {
        val patch = PendingPatch(7, "failed", "TCP timeout after 5s")
        val decoded = PendingStatusRepository.decode(PendingStatusRepository.encode(listOf(patch)))
        assertEquals("TCP timeout after 5s", decoded[0].errorMessage)
    }

    @Test
    fun `entry missing status field is silently dropped`() {
        val json = """[{"jobId": 1}, {"jobId": 2, "status": "printed"}]"""
        val decoded = PendingStatusRepository.decode(json)
        assertEquals(1, decoded.size)
        assertEquals(2, decoded[0].jobId)
    }

    @Test
    fun `entry with negative jobId is silently dropped`() {
        val json = """[{"jobId": -1, "status": "printed"}]"""
        assertTrue(PendingStatusRepository.decode(json).isEmpty())
    }

    @Test
    fun `multiple entries encode and decode in order`() {
        val patches = (1..5).map { PendingPatch(it, "printed", null) }
        val decoded = PendingStatusRepository.decode(PendingStatusRepository.encode(patches))
        assertEquals(patches.map { it.jobId }, decoded.map { it.jobId })
    }
}
