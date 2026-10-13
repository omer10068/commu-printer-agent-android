package com.commu.printeragent.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.ByteArrayOutputStream

/**
 * Renders receipt text to ESC/POS GS v 0 raster bytes using Android's Canvas API.
 *
 * Android's text renderer handles Hebrew/RTL BiDi automatically via StaticLayout.
 * No Hebrew code-page encoding is needed; the printer receives a 1-bit bitmap.
 *
 * Printer parameters assumed: 576 dots wide (80 mm at 203 DPI), 72 bytes per line.
 * Output: GS v 0 raster section, ready to embed in a full ESC/POS stream
 * (wrap with [EscPosEncoder.buildRasterReceipt] or add INIT/CUT manually).
 *
 * This class is Android-only (requires Canvas + Bitmap) and cannot be JVM unit-tested.
 * Pure layout math lives in ReceiptLayoutCalc.kt and is fully JVM-testable.
 */
object ReceiptRenderer {

    private const val DOTS_WIDTH = EscPosEncoder.PAPER_WIDTH_DOTS  // 576

    /**
     * Renders [text] to ESC/POS raster bytes.
     * [textSizePx] is the starting font size; calibration may adjust it.
     */
    fun renderText(text: String, textSizePx: Float = 28f): ByteArray {
        val bitmap = renderBitmap(text, textSizePx)
        return bitmapToGsV0(bitmap).also { bitmap.recycle() }
    }

    /**
     * Development-only. Renders [text] and saves the intermediate bitmap as a PNG.
     *
     * Usage on device:
     *   ReceiptRenderer.saveDebugPreview(text, context.getExternalFilesDir(null)
     *       ?.resolve("receipt_preview.png") ?: return)
     *   // then: adb pull /sdcard/Android/data/<pkg>/files/receipt_preview.png
     *
     * Not called from the production job-processing pipeline.
     */
    fun saveDebugPreview(text: String, outputFile: java.io.File, textSizePx: Float = 28f) {
        val bitmap = renderBitmap(text, textSizePx)
        try {
            outputFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }

    // ── Internal rendering ──────────────────────────────────────────────────────

    private fun renderBitmap(text: String, textSizePx: Float): Bitmap {
        // Content is drawn inside [HORIZONTAL_INSET, DOTS_WIDTH - HORIZONTAL_INSET].
        // This leaves a safe blank margin on each side to absorb the printer's internal
        // raster offset (~14 dots observed on SNBC BTP-R880NPII) and Hebrew glyph overflow.
        val contentWidth = DOTS_WIDTH - 2 * HORIZONTAL_INSET

        val textPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = textSizePx
            typeface = Typeface.MONOSPACE
            isAntiAlias = false
        }

        // Scale so that exactly 42 monospace chars fill contentWidth dots.
        textPaint.textSize = calibrateFontSize(
            initialSize       = textPaint.textSize,
            measuredCharWidth = textPaint.measureText("0"),
            dotsWidth         = contentWidth,
        )

        // LTR paragraph direction: Hebrew RTL runs are shaped correctly within LTR context.
        // Separator lines (neutral chars) stay left-to-right, not subject to RTL reordering.
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.2f)
            .setIncludePad(false)
            .setTextDirection(TextDirectionHeuristics.LTR)
            .build()

        val bitmapHeight = (layout.height + textPaint.textSize.toInt()).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(DOTS_WIDTH, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.translate(HORIZONTAL_INSET.toFloat(), 0f)
        layout.draw(canvas)
        canvas.restore()

        return bitmap
    }

    private fun bitmapToGsV0(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val out = ByteArrayOutputStream()

        // GS v 0 header: m=0 (normal), xL, xH, yL, yH
        out.write(byteArrayOf(
            0x1D, 0x76, 0x30, 0x00,
            (widthBytes and 0xFF).toByte(),
            ((widthBytes shr 8) and 0xFF).toByte(),
            (height and 0xFF).toByte(),
            ((height shr 8) and 0xFF).toByte(),
        ))

        for (y in 0 until height) {
            for (byteIdx in 0 until widthBytes) {
                var b = 0
                for (bit in 0..7) {
                    val x = byteIdx * 8 + bit
                    if (x < width) {
                        val pixel = pixels[y * width + x]
                        val lum = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                        if (lum < 128) b = b or (0x80 ushr bit)
                    }
                }
                out.write(b)
            }
        }

        return out.toByteArray()
    }
}
