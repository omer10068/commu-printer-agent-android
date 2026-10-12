package com.commu.printeragent.printing

import java.io.ByteArrayOutputStream

/**
 * Assembles ESC/POS byte sequences for raw TCP receipt printing.
 *
 * This object is pure Kotlin with no Android dependencies — it is fully
 * unit-testable in a JVM environment. It does NOT render Hebrew text;
 * use [ReceiptRenderer] for bitmap rasterization of non-Latin content.
 *
 * ESC/POS reference: Epson ESC/POS Application Programming Guide (generalized).
 * Thermal receipt width assumed: 576 dots (80 mm at 203 DPI).
 */
object EscPosEncoder {

    const val PAPER_WIDTH_DOTS = 576
    const val PAPER_WIDTH_CHARS = 42

    // ── Command bytes (each access returns a fresh copy — ByteArray is mutable) ──

    val INIT: ByteArray         get() = byteArrayOf(0x1B, 0x40)              // ESC @
    val ALIGN_LEFT: ByteArray   get() = byteArrayOf(0x1B, 0x61, 0x00)        // ESC a 0
    val ALIGN_CENTER: ByteArray get() = byteArrayOf(0x1B, 0x61, 0x01)        // ESC a 1
    val ALIGN_RIGHT: ByteArray  get() = byteArrayOf(0x1B, 0x61, 0x02)        // ESC a 2
    val BOLD_ON: ByteArray      get() = byteArrayOf(0x1B, 0x45, 0x01)        // ESC E 1
    val BOLD_OFF: ByteArray     get() = byteArrayOf(0x1B, 0x45, 0x00)        // ESC E 0
    val DOUBLE_HEIGHT_ON: ByteArray  get() = byteArrayOf(0x1B, 0x21, 0x10)   // ESC ! 16
    val DOUBLE_HEIGHT_OFF: ByteArray get() = byteArrayOf(0x1B, 0x21, 0x00)   // ESC ! 0
    val PARTIAL_CUT: ByteArray  get() = byteArrayOf(0x1D, 0x56, 0x42, 0x03)  // GS V B 3

    // ── Primitive builders ─────────────────────────────────────────────────────

    fun lineFeed(count: Int = 1): ByteArray = ByteArray(count) { 0x0A }

    /** Encodes one line as ISO-8859-1 bytes followed by a line-feed. */
    fun textLine(text: String): ByteArray =
        text.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0x0A)

    /** Produces a separator of [width] repeated [char] characters + LF. */
    fun separator(width: Int = PAPER_WIDTH_CHARS, char: Char = '-'): ByteArray =
        textLine(char.toString().repeat(width))

    /**
     * Wraps pre-computed raster bitmap data in a GS v 0 (normal density) header.
     *
     * @param widthBytes  number of bytes per raster line (= ceil(dotWidth / 8))
     * @param heightDots  number of raster lines
     * @param data        row-major 1-bit pixels: MSB is the leftmost dot, 1 = black
     */
    fun rasterBitmap(widthBytes: Int, heightDots: Int, data: ByteArray): ByteArray {
        require(data.size == widthBytes * heightDots) {
            "Raster data size mismatch: expected ${widthBytes * heightDots}, got ${data.size}"
        }
        val header = byteArrayOf(
            0x1D, 0x76, 0x30, 0x00,
            (widthBytes and 0xFF).toByte(),
            ((widthBytes shr 8) and 0xFF).toByte(),
            (heightDots and 0xFF).toByte(),
            ((heightDots shr 8) and 0xFF).toByte(),
        )
        return header + data
    }

    // ── Complete receipt builders ──────────────────────────────────────────────

    /**
     * Builds a self-contained ASCII-only test receipt.
     * Safe for all code page settings; used to verify TCP connectivity and
     * printer mechanics before Hebrew raster printing is attempted.
     */
    fun buildTestReceipt(printerName: String = ""): ByteArray {
        val out = ByteArrayOutputStream()
        fun w(b: ByteArray) = out.write(b)

        w(INIT)
        w(ALIGN_CENTER)
        w(BOLD_ON)
        w(textLine("COMMU PRINTER AGENT"))
        w(BOLD_OFF)
        w(textLine("  -- TEST PRINT --  "))
        w(separator())
        w(ALIGN_LEFT)
        if (printerName.isNotBlank()) w(textLine("Printer : $printerName"))
        w(textLine("Status  : TCP socket OK"))
        w(textLine("Charset : ESC/POS RAW / ISO-8859-1"))
        w(textLine("Hebrew  : requires raster mode"))
        w(separator())
        w(lineFeed(4))
        w(PARTIAL_CUT)

        return out.toByteArray()
    }

    /**
     * Builds a receipt that wraps pre-rasterized [bitmapBytes] (from [ReceiptRenderer])
     * between an ESC/POS init and a cut command.
     */
    fun buildRasterReceipt(bitmapBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(INIT)
        out.write(bitmapBytes)
        out.write(lineFeed(4))
        out.write(PARTIAL_CUT)
        return out.toByteArray()
    }
}
