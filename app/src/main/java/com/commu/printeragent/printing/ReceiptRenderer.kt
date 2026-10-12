package com.commu.printeragent.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.graphics.Typeface
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
 */
object ReceiptRenderer {

    private const val DOTS_WIDTH = EscPosEncoder.PAPER_WIDTH_DOTS  // 576

    /**
     * Renders [text] to ESC/POS raster bytes.
     * [textSizePx] controls the font size in canvas pixels; 28px ≈ readable at 203 DPI.
     */
    fun renderText(text: String, textSizePx: Float = 28f): ByteArray {
        val textPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = textSizePx
            typeface = Typeface.DEFAULT
            isAntiAlias = false
        }

        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, DOTS_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.2f)
            .setIncludePad(false)
            .build()

        val bitmapHeight = (layout.height + textSizePx.toInt()).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(DOTS_WIDTH, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        layout.draw(canvas)

        return bitmapToGsV0(bitmap).also { bitmap.recycle() }
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
