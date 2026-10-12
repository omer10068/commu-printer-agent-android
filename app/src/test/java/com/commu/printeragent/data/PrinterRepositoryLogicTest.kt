package com.commu.printeragent.data

import com.commu.printeragent.data.PrinterRecord.Companion.applyEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterRepositoryLogicTest {

    // ── parseIdList ───────────────────────────────────────────────────────────

    @Test
    fun `parseIdList empty string returns empty list`() {
        assertEquals(emptyList<String>(), PrinterRepository.parseIdList(""))
    }

    @Test
    fun `parseIdList blank string returns empty list`() {
        assertEquals(emptyList<String>(), PrinterRepository.parseIdList("   "))
    }

    @Test
    fun `parseIdList single id`() {
        assertEquals(listOf("abc-123"), PrinterRepository.parseIdList("abc-123"))
    }

    @Test
    fun `parseIdList multiple ids`() {
        val ids = PrinterRepository.parseIdList("id1,id2,id3")
        assertEquals(listOf("id1", "id2", "id3"), ids)
    }

    @Test
    fun `parseIdList trims whitespace around ids`() {
        val ids = PrinterRepository.parseIdList("id1 , id2 , id3")
        assertEquals(listOf("id1", "id2", "id3"), ids)
    }

    @Test
    fun `parseIdList ignores blank segments`() {
        val ids = PrinterRepository.parseIdList("id1,,id2")
        assertEquals(listOf("id1", "id2"), ids)
    }

    // ── formatIdList ──────────────────────────────────────────────────────────

    @Test
    fun `formatIdList empty list produces empty string`() {
        assertEquals("", PrinterRepository.formatIdList(emptyList()))
    }

    @Test
    fun `formatIdList round-trips through parseIdList`() {
        val ids = listOf("uuid-a", "uuid-b", "uuid-c")
        val encoded = PrinterRepository.formatIdList(ids)
        assertEquals(ids, PrinterRepository.parseIdList(encoded))
    }

    // ── key name format ───────────────────────────────────────────────────────

    @Test
    fun `key names use correct prefix pattern`() {
        val id = "test-id"
        assertEquals("p_test-id_name",     PrinterRepository.keyName(id).name)
        assertEquals("p_test-id_addr",     PrinterRepository.keyAddress(id).name)
        assertEquals("p_test-id_port",     PrinterRepository.keyPort(id).name)
        assertEquals("p_test-id_notes",    PrinterRepository.keyNotes(id).name)
        assertEquals("p_test-id_primary",  PrinterRepository.keyPrimary(id).name)
        assertEquals("p_test-id_check_ms", PrinterRepository.keyCheckMs(id).name)
        assertEquals("p_test-id_check_ok", PrinterRepository.keyCheckOk(id).name)
    }

    @Test
    fun `two different printer ids produce different keys`() {
        val keyA = PrinterRepository.keyName("printer-a")
        val keyB = PrinterRepository.keyName("printer-b")
        assertFalse("Keys for different printer IDs must differ", keyA == keyB)
    }

    // ── Multi-printer isolation ───────────────────────────────────────────────

    @Test
    fun `adding second id to list preserves first`() {
        val initial = PrinterRepository.formatIdList(listOf("printer-1"))
        val ids = PrinterRepository.parseIdList(initial).toMutableList()
        ids.add("printer-2")
        val result = PrinterRepository.parseIdList(PrinterRepository.formatIdList(ids))
        assertTrue(result.contains("printer-1"))
        assertTrue(result.contains("printer-2"))
        assertEquals(2, result.size)
    }

    @Test
    fun `removing one id from list preserves others`() {
        val encoded = PrinterRepository.formatIdList(listOf("p1", "p2", "p3"))
        val afterRemove = PrinterRepository.parseIdList(encoded).filter { it != "p2" }
        assertEquals(listOf("p1", "p3"), afterRemove)
    }

    @Test
    fun `insertion order is preserved`() {
        val ids = listOf("first", "second", "third")
        val encoded = PrinterRepository.formatIdList(ids)
        assertEquals(ids, PrinterRepository.parseIdList(encoded))
    }

    // ── PrinterRecord.applyEdit — check invalidation ──────────────────────────

    private fun record(addr: String = "192.168.1.1", port: Int = 9100) =
        PrinterRecord("id1", "Printer", addr, port, lastCheckMs = 999L, lastCheckOk = true)

    @Test
    fun `applyEdit preserves check fields when only name changes`() {
        val r = record()
        val updated = applyEdit(r, name = "New Name", address = r.address, port = r.port, notes = "")
        assertEquals(999L, updated.lastCheckMs)
        assertTrue(updated.lastCheckOk)
    }

    @Test
    fun `applyEdit resets check fields when address changes`() {
        val r = record("192.168.1.1")
        val updated = applyEdit(r, name = r.name, address = "192.168.1.2", port = r.port, notes = "")
        assertEquals(0L, updated.lastCheckMs)
        assertFalse(updated.lastCheckOk)
    }

    @Test
    fun `applyEdit resets check fields when port changes`() {
        val r = record(port = 9100)
        val updated = applyEdit(r, name = r.name, address = r.address, port = 9101, notes = "")
        assertEquals(0L, updated.lastCheckMs)
        assertFalse(updated.lastCheckOk)
    }

    @Test
    fun `applyEdit preserves isPrimary from original`() {
        val r = record().copy(isPrimary = true)
        val updated = applyEdit(r, name = "New", address = "10.0.0.1", port = 9100, notes = "")
        assertTrue(updated.isPrimary)
    }

    @Test
    fun `applyEdit applies all non-check fields`() {
        val r = record()
        val updated = applyEdit(r, name = "Changed", address = "10.0.0.5", port = 515, notes = "note")
        assertEquals("Changed", updated.name)
        assertEquals("10.0.0.5", updated.address)
        assertEquals(515, updated.port)
        assertEquals("note", updated.notes)
        assertEquals("id1", updated.id)
    }
}
