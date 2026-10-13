package com.commu.printeragent.printing

/**
 * Pure (Android-free) receipt layout math — fully JVM-testable.
 */

/**
 * Safe horizontal inset applied on each side of the printed bitmap (dots).
 *
 * The SNBC BTP-R880NPII (and similar thermal printers) applies a small internal
 * offset to all GS v 0 raster data, shifting it ~1 character-width to the right
 * relative to the logical printable area.  Drawing content all the way to x=0 and
 * x=DOTS_WIDTH therefore clips the leftmost and rightmost characters.
 *
 * 24 dots ≈ 3 mm at 203 DPI — larger than the observed clipping offset (~14 dots /
 * 1 char-width) and large enough to absorb Hebrew fallback glyph overflow.
 */
internal const val HORIZONTAL_INSET = 24

/**
 * Returns a new text size so that [charsPerLine] monospace characters
 * exactly span [dotsWidth] printer dots.
 *
 * @param initialSize        starting TextPaint.textSize in canvas pixels
 * @param measuredCharWidth  width of one "0" character measured at [initialSize]
 * @param dotsWidth          total printable width in dots (576 = 80 mm @ 203 DPI)
 * @param charsPerLine       characters per line (42 for a standard 80 mm roll)
 * @return calibrated text size, or [initialSize] unchanged when [measuredCharWidth] ≤ 0
 */
internal fun calibrateFontSize(
    initialSize: Float,
    measuredCharWidth: Float,
    dotsWidth: Int = EscPosEncoder.PAPER_WIDTH_DOTS,
    charsPerLine: Int = EscPosEncoder.PAPER_WIDTH_CHARS,
): Float {
    if (measuredCharWidth <= 0f) return initialSize
    val targetCharWidth = dotsWidth.toFloat() / charsPerLine
    return initialSize * (targetCharWidth / measuredCharWidth)
}
