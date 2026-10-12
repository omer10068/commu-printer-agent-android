package com.commu.printeragent.printing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosEncoderTest {

    // ── Primitive commands ────────────────────────────────────────────────────

    @Test
    fun `INIT is ESC @`() {
        assertArrayEquals(byteArrayOf(0x1B, 0x40), EscPosEncoder.INIT)
    }

    @Test
    fun `PARTIAL_CUT is GS V B 3`() {
        assertArrayEquals(byteArrayOf(0x1D, 0x56, 0x42, 0x03), EscPosEncoder.PARTIAL_CUT)
    }

    @Test
    fun `ALIGN_CENTER is ESC a 1`() {
        assertArrayEquals(byteArrayOf(0x1B, 0x61, 0x01), EscPosEncoder.ALIGN_CENTER)
    }

    @Test
    fun `lineFeed count 1 produces single LF`() {
        assertArrayEquals(byteArrayOf(0x0A), EscPosEncoder.lineFeed(1))
    }

    @Test
    fun `lineFeed count 3 produces three LF bytes`() {
        assertEquals(3, EscPosEncoder.lineFeed(3).size)
        assertTrue(EscPosEncoder.lineFeed(3).all { it == 0x0A.toByte() })
    }

    @Test
    fun `textLine appends LF to content`() {
        val result = EscPosEncoder.textLine("AB")
        assertEquals(3, result.size)
        assertEquals('A'.code.toByte(), result[0])
        assertEquals('B'.code.toByte(), result[1])
        assertEquals(0x0A.toByte(), result[2])
    }

    @Test
    fun `separator has correct length including LF`() {
        val sep = EscPosEncoder.separator(42, '-')
        assertEquals(43, sep.size)  // 42 chars + LF
    }

    @Test
    fun `separator uses specified char`() {
        val sep = EscPosEncoder.separator(5, '=')
        assertEquals('='.code.toByte(), sep[0])
        assertEquals('='.code.toByte(), sep[4])
        assertEquals(0x0A.toByte(), sep[5])
    }

    // ── Raster bitmap ─────────────────────────────────────────────────────────

    @Test
    fun `rasterBitmap starts with GS v 0 header`() {
        val data = ByteArray(72 * 10)  // 72 bytes wide, 10 lines
        val result = EscPosEncoder.rasterBitmap(72, 10, data)
        assertEquals(0x1D.toByte(), result[0])  // GS
        assertEquals(0x76.toByte(), result[1])  // v
        assertEquals(0x30.toByte(), result[2])  // 0
        assertEquals(0x00.toByte(), result[3])  // m=0
    }

    @Test
    fun `rasterBitmap encodes width and height in header`() {
        val widthBytes = 72
        val heightDots = 100
        val data = ByteArray(widthBytes * heightDots)
        val result = EscPosEncoder.rasterBitmap(widthBytes, heightDots, data)
        assertEquals((widthBytes and 0xFF).toByte(), result[4])   // xL
        assertEquals(((widthBytes shr 8) and 0xFF).toByte(), result[5])  // xH
        assertEquals((heightDots and 0xFF).toByte(), result[6])   // yL
        assertEquals(((heightDots shr 8) and 0xFF).toByte(), result[7])  // yH
    }

    @Test
    fun `rasterBitmap total length is 8 byte header plus data`() {
        val data = ByteArray(72 * 20)
        val result = EscPosEncoder.rasterBitmap(72, 20, data)
        assertEquals(8 + data.size, result.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rasterBitmap throws on data size mismatch`() {
        EscPosEncoder.rasterBitmap(72, 10, ByteArray(1))
    }

    // ── Test receipt ──────────────────────────────────────────────────────────

    @Test
    fun `buildTestReceipt starts with INIT`() {
        val bytes = EscPosEncoder.buildTestReceipt()
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])
    }

    @Test
    fun `buildTestReceipt ends with PARTIAL_CUT`() {
        val bytes = EscPosEncoder.buildTestReceipt()
        val cut = EscPosEncoder.PARTIAL_CUT
        val tail = bytes.takeLast(cut.size).toByteArray()
        assertArrayEquals(cut, tail)
    }

    @Test
    fun `buildTestReceipt is non-empty`() {
        assertTrue(EscPosEncoder.buildTestReceipt().isNotEmpty())
    }

    @Test
    fun `buildTestReceipt includes BOLD_ON and BOLD_OFF`() {
        val bytes = EscPosEncoder.buildTestReceipt("TestPrinter")
        val list = bytes.toList()
        assertTrue(list.containsAll(EscPosEncoder.BOLD_ON.toList()))
        assertTrue(list.containsAll(EscPosEncoder.BOLD_OFF.toList()))
    }

    @Test
    fun `buildRasterReceipt wraps payload between INIT and PARTIAL_CUT`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val bytes = EscPosEncoder.buildRasterReceipt(payload)
        // Starts with INIT
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])
        // Ends with PARTIAL_CUT
        val cut = EscPosEncoder.PARTIAL_CUT
        val tail = bytes.takeLast(cut.size).toByteArray()
        assertArrayEquals(cut, tail)
        // Contains the payload bytes
        val asList = bytes.toList()
        assertTrue(asList.containsAll(payload.toList()))
    }
}
