package com.commu.printeragent.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterMappingTest {

    // ── encode ────────────────────────────────────────────────────────────────

    @Test
    fun `encode empty map produces blank string`() {
        assertEquals("", PrinterMappingRepository.encode(emptyMap()))
    }

    @Test
    fun `encode single entry produces id-colon-uuid`() {
        val result = PrinterMappingRepository.encode(mapOf(1 to "uuid-abc"))
        assertEquals("1:uuid-abc", result)
    }

    @Test
    fun `encode two entries produces comma-separated pairs`() {
        val map = linkedMapOf(1 to "uuid-a", 2 to "uuid-b")
        val result = PrinterMappingRepository.encode(map)
        assertTrue(result.contains("1:uuid-a"))
        assertTrue(result.contains("2:uuid-b"))
        assertTrue(result.contains(","))
    }

    // ── decode ────────────────────────────────────────────────────────────────

    @Test
    fun `decode blank string returns empty map`() {
        assertTrue(PrinterMappingRepository.decode("").isEmpty())
        assertTrue(PrinterMappingRepository.decode("   ").isEmpty())
    }

    @Test
    fun `decode single entry`() {
        val result = PrinterMappingRepository.decode("1:uuid-abc")
        assertEquals(1, result.size)
        assertEquals("uuid-abc", result[1])
    }

    @Test
    fun `decode two entries`() {
        val result = PrinterMappingRepository.decode("1:uuid-a,2:uuid-b")
        assertEquals(2, result.size)
        assertEquals("uuid-a", result[1])
        assertEquals("uuid-b", result[2])
    }

    @Test
    fun `decode ignores entry with no colon`() {
        val result = PrinterMappingRepository.decode("nocolon,1:uuid-a")
        assertEquals(1, result.size)
        assertEquals("uuid-a", result[1])
    }

    @Test
    fun `decode ignores entry with non-integer id`() {
        val result = PrinterMappingRepository.decode("abc:uuid-a,2:uuid-b")
        assertEquals(1, result.size)
        assertEquals("uuid-b", result[2])
    }

    @Test
    fun `decode ignores entry with blank uuid`() {
        val result = PrinterMappingRepository.decode("1:,2:uuid-b")
        assertEquals(1, result.size)
        assertEquals("uuid-b", result[2])
    }

    // ── round-trip ────────────────────────────────────────────────────────────

    @Test
    fun `encode then decode round-trips the map`() {
        val original = mapOf(1 to "uuid-aaaa", 2 to "uuid-bbbb", 7 to "uuid-cccc")
        val encoded = PrinterMappingRepository.encode(original)
        val decoded = PrinterMappingRepository.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `empty map round-trips`() {
        val encoded = PrinterMappingRepository.encode(emptyMap())
        val decoded = PrinterMappingRepository.decode(encoded)
        assertTrue(decoded.isEmpty())
    }

    // ── routing logic ─────────────────────────────────────────────────────────

    @Test
    fun `given backendId maps to correct local uuid`() {
        val mappings = mapOf(1 to "local-uuid-kitchen", 2 to "local-uuid-bar")
        assertEquals("local-uuid-kitchen", mappings[1])
        assertEquals("local-uuid-bar", mappings[2])
        assertNull(mappings[99])
    }

    @Test
    fun `two backend printers map to different local printers`() {
        val mappings = mapOf(1 to "uuid-a", 2 to "uuid-b")
        val localA = mappings[1]
        val localB = mappings[2]
        assertTrue(localA != localB)
    }

    @Test
    fun `reverse lookup finds backendId for a localUUID`() {
        val mappings = mapOf(1 to "uuid-a", 2 to "uuid-b")
        val byLocal = mappings.entries.associateBy({ it.value }, { it.key })
        assertEquals(1, byLocal["uuid-a"])
        assertEquals(2, byLocal["uuid-b"])
        assertNull(byLocal["uuid-missing"])
    }

    // ── removeByLocalId (in-memory logic) ────────────────────────────────────

    @Test
    fun `filterValues removes entry with matching localId`() {
        val mappings = mapOf(1 to "uuid-kitchen", 2 to "uuid-bar")
        val result = mappings.filterValues { it != "uuid-kitchen" }
        assertEquals(1, result.size)
        assertNull(result[1])
        assertEquals("uuid-bar", result[2])
    }

    @Test
    fun `filterValues on unknown localId leaves map unchanged`() {
        val mappings = mapOf(1 to "uuid-a", 2 to "uuid-b")
        val result = mappings.filterValues { it != "uuid-missing" }
        assertEquals(2, result.size)
    }

    @Test
    fun `filterValues removes all entries with the same localId`() {
        val mappings = mapOf(1 to "uuid-shared", 2 to "uuid-shared", 3 to "uuid-other")
        val result = mappings.filterValues { it != "uuid-shared" }
        assertEquals(1, result.size)
        assertEquals("uuid-other", result[3])
    }
}
