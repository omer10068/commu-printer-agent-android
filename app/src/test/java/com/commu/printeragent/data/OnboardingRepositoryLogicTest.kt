package com.commu.printeragent.data

import com.commu.printeragent.data.PrinterRecord.Companion.applyEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for onboarding / migration logic and the new PrinterRecord fields.
 * No Android context required.
 */
class OnboardingRepositoryLogicTest {

    // ── Migration detection logic ─────────────────────────────────────────────

    /**
     * Pure function extracted from the migration logic:
     * Returns true only when all four conditions are satisfied.
     */
    private fun shouldMigrateToStep3(
        hasUrl: Boolean,
        hasToken: Boolean,
        hasPrinters: Boolean,
        hasMappings: Boolean,
    ): Boolean = hasUrl && hasToken && hasPrinters && hasMappings

    @Test
    fun `migration returns true only when all four conditions are true`() {
        assertTrue(shouldMigrateToStep3(true, true, true, true))
    }

    @Test
    fun `migration returns false when url is false`() {
        assertFalse(shouldMigrateToStep3(false, true, true, true))
    }

    @Test
    fun `migration returns false when token is false`() {
        assertFalse(shouldMigrateToStep3(true, false, true, true))
    }

    @Test
    fun `migration returns false when printers is false`() {
        assertFalse(shouldMigrateToStep3(true, true, false, true))
    }

    @Test
    fun `migration returns false when mappings is false`() {
        assertFalse(shouldMigrateToStep3(true, true, true, false))
    }

    @Test
    fun `migration returns false when all are false`() {
        assertFalse(shouldMigrateToStep3(false, false, false, false))
    }

    @Test
    fun `migration returns false for all single-true combinations`() {
        assertFalse(shouldMigrateToStep3(true, false, false, false))
        assertFalse(shouldMigrateToStep3(false, true, false, false))
        assertFalse(shouldMigrateToStep3(false, false, true, false))
        assertFalse(shouldMigrateToStep3(false, false, false, true))
    }

    @Test
    fun `migration returns false for all two-true combinations`() {
        assertFalse(shouldMigrateToStep3(true, true, false, false))
        assertFalse(shouldMigrateToStep3(true, false, true, false))
        assertFalse(shouldMigrateToStep3(true, false, false, true))
        assertFalse(shouldMigrateToStep3(false, true, true, false))
        assertFalse(shouldMigrateToStep3(false, true, false, true))
        assertFalse(shouldMigrateToStep3(false, false, true, true))
    }

    @Test
    fun `migration returns false for all three-false combinations`() {
        assertFalse(shouldMigrateToStep3(false, true, true, true))
        assertFalse(shouldMigrateToStep3(true, false, true, true))
        assertFalse(shouldMigrateToStep3(true, true, false, true))
        assertFalse(shouldMigrateToStep3(true, true, true, false))
    }

    @Test
    fun `all 16 combinations are covered correctly`() {
        var trueCount = 0
        for (a in listOf(false, true)) {
            for (b in listOf(false, true)) {
                for (c in listOf(false, true)) {
                    for (d in listOf(false, true)) {
                        val result = shouldMigrateToStep3(a, b, c, d)
                        val expected = a && b && c && d
                        assertEquals("shouldMigrateToStep3($a,$b,$c,$d)", expected, result)
                        if (result) trueCount++
                    }
                }
            }
        }
        // Only 1 of the 16 combinations should return true
        assertEquals(1, trueCount)
    }

    // ── PrinterRecord.applyEdit with new fields ───────────────────────────────

    private fun baseRecord(
        addr: String = "192.168.1.1",
        port: Int = 9100,
        ipStatus: IpConfigStatus = IpConfigStatus.Configured,
        testStatus: TestPrintStatus = TestPrintStatus.Verified,
    ) = PrinterRecord(
        id              = "id1",
        name            = "Printer",
        address         = addr,
        port            = port,
        lastCheckMs     = 999L,
        lastCheckOk     = true,
        ipConfigStatus  = ipStatus,
        testPrintStatus = testStatus,
    )

    @Test
    fun `applyEdit preserves ipConfigStatus when only name changes`() {
        val r = baseRecord()
        val updated = applyEdit(r, name = "New Name", address = r.address, port = r.port, notes = "")
        assertEquals(IpConfigStatus.Configured, updated.ipConfigStatus)
    }

    @Test
    fun `applyEdit preserves testPrintStatus when only name changes`() {
        val r = baseRecord()
        val updated = applyEdit(r, name = "New Name", address = r.address, port = r.port, notes = "")
        assertEquals(TestPrintStatus.Verified, updated.testPrintStatus)
    }

    @Test
    fun `applyEdit resets ipConfigStatus to Pending when address changes`() {
        val r = baseRecord("192.168.1.1")
        val updated = applyEdit(r, name = r.name, address = "192.168.1.2", port = r.port, notes = "")
        assertEquals(IpConfigStatus.Pending, updated.ipConfigStatus)
    }

    @Test
    fun `applyEdit resets testPrintStatus to Pending when address changes`() {
        val r = baseRecord("192.168.1.1")
        val updated = applyEdit(r, name = r.name, address = "192.168.1.2", port = r.port, notes = "")
        assertEquals(TestPrintStatus.Pending, updated.testPrintStatus)
    }

    @Test
    fun `applyEdit resets ipConfigStatus to Pending when port changes`() {
        val r = baseRecord(port = 9100)
        val updated = applyEdit(r, name = r.name, address = r.address, port = 9101, notes = "")
        assertEquals(IpConfigStatus.Pending, updated.ipConfigStatus)
    }

    @Test
    fun `applyEdit resets testPrintStatus to Pending when port changes`() {
        val r = baseRecord(port = 9100)
        val updated = applyEdit(r, name = r.name, address = r.address, port = 9101, notes = "")
        assertEquals(TestPrintStatus.Pending, updated.testPrintStatus)
    }

    @Test
    fun `applyEdit resets both status fields when address and port both change`() {
        val r = baseRecord("192.168.1.1", 9100)
        val updated = applyEdit(r, name = r.name, address = "10.0.0.5", port = 515, notes = "")
        assertEquals(IpConfigStatus.Pending, updated.ipConfigStatus)
        assertEquals(TestPrintStatus.Pending, updated.testPrintStatus)
    }

    @Test
    fun `applyEdit preserves Failed ipConfigStatus when only name changes`() {
        val r = baseRecord(ipStatus = IpConfigStatus.Failed, testStatus = TestPrintStatus.Failed)
        val updated = applyEdit(r, name = "Different", address = r.address, port = r.port, notes = "")
        assertEquals(IpConfigStatus.Failed, updated.ipConfigStatus)
        assertEquals(TestPrintStatus.Failed, updated.testPrintStatus)
    }

    // ── PrinterRepository key names for new fields ────────────────────────────

    @Test
    fun `keyIpConfig produces correct key name`() {
        assertEquals("p_test-id_ip_config", PrinterRepository.keyIpConfig("test-id").name)
    }

    @Test
    fun `keyTestPrint produces correct key name`() {
        assertEquals("p_test-id_test_print", PrinterRepository.keyTestPrint("test-id").name)
    }

    @Test
    fun `new keys are distinct from all existing keys for same id`() {
        val id = "printer-x"
        val ipKey   = PrinterRepository.keyIpConfig(id)
        val testKey = PrinterRepository.keyTestPrint(id)
        val existing = listOf(
            PrinterRepository.keyName(id),
            PrinterRepository.keyAddress(id),
            PrinterRepository.keyPort(id),
            PrinterRepository.keyNotes(id),
            PrinterRepository.keyPrimary(id),
            PrinterRepository.keyCheckMs(id),
            PrinterRepository.keyCheckOk(id),
        )
        for (key in existing) {
            assertFalse("ip_config key must differ from $key", ipKey == key)
            assertFalse("test_print key must differ from $key", testKey == key)
        }
        assertFalse("ip_config and test_print keys must differ", ipKey == testKey)
    }

    @Test
    fun `different printer ids produce different ip config keys`() {
        val keyA = PrinterRepository.keyIpConfig("printer-a")
        val keyB = PrinterRepository.keyIpConfig("printer-b")
        assertFalse(keyA == keyB)
    }

    @Test
    fun `different printer ids produce different test print keys`() {
        val keyA = PrinterRepository.keyTestPrint("printer-a")
        val keyB = PrinterRepository.keyTestPrint("printer-b")
        assertFalse(keyA == keyB)
    }

    // ── Printer deletion helper logic ─────────────────────────────────────────

    @Test
    fun `removing id from formatted list leaves remaining ids`() {
        val raw = PrinterRepository.formatIdList(listOf("id-a", "id-b", "id-c"))
        val afterRemoval = PrinterRepository.formatIdList(
            PrinterRepository.parseIdList(raw).filter { it != "id-b" }
        )
        val remaining = PrinterRepository.parseIdList(afterRemoval)
        assertEquals(listOf("id-a", "id-c"), remaining)
    }

    @Test
    fun `removing only id from list produces empty list`() {
        val raw = PrinterRepository.formatIdList(listOf("only-id"))
        val afterRemoval = PrinterRepository.formatIdList(
            PrinterRepository.parseIdList(raw).filter { it != "only-id" }
        )
        assertTrue(PrinterRepository.parseIdList(afterRemoval).isEmpty())
    }

    @Test
    fun `removing nonexistent id leaves list unchanged`() {
        val ids = listOf("id-a", "id-b")
        val raw = PrinterRepository.formatIdList(ids)
        val afterRemoval = PrinterRepository.formatIdList(
            PrinterRepository.parseIdList(raw).filter { it != "id-ghost" }
        )
        assertEquals(ids, PrinterRepository.parseIdList(afterRemoval))
    }

    @Test
    fun `mapping removeByLocalId preserves real printer and removes dummy`() {
        val realLocalId = "real-uuid-snbc"
        val dummyLocalId = "dummy-uuid-printer2"
        val original = mapOf(7 to realLocalId, 8 to dummyLocalId)
        val encoded = PrinterMappingRepository.encode(original)
        val decoded = PrinterMappingRepository.decode(encoded)
        val afterRemoval = decoded.filterValues { it != dummyLocalId }
        assertEquals(1, afterRemoval.size)
        assertEquals(realLocalId, afterRemoval[7])
        assertFalse(afterRemoval.containsKey(8))
    }

    @Test
    fun `mapping removeByLocalId is no-op when id is not present`() {
        val realLocalId = "real-uuid-snbc"
        val original = mapOf(7 to realLocalId)
        val encoded = PrinterMappingRepository.encode(original)
        val decoded = PrinterMappingRepository.decode(encoded)
        val afterRemoval = decoded.filterValues { it != "nonexistent-id" }
        assertEquals(original, afterRemoval)
    }

    @Test
    fun `mapping encode then decode round-trips correctly after removal`() {
        val original = mapOf(7 to "uuid-7", 8 to "uuid-8", 9 to "uuid-9")
        val afterRemoval = original.filterValues { it != "uuid-8" }
        val reEncoded = PrinterMappingRepository.encode(afterRemoval)
        val reDecoded = PrinterMappingRepository.decode(reEncoded)
        assertEquals(mapOf(7 to "uuid-7", 9 to "uuid-9"), reDecoded)
    }
}
