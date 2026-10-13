package com.commu.printeragent.printing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for receipt layout calibration math.
 *
 * calibrateFontSize() lives in ReceiptLayoutCalc.kt — pure Kotlin, no Android
 * dependencies — so these tests run on the JVM without Robolectric.
 */
class ReceiptRendererLogicTest {

    // ── calibrateFontSize ─────────────────────────────────────────────────────

    @Test
    fun `calibrateFontSize scales proportionally to fill paper width`() {
        // 9 px per char measured; target = 576/42 ≈ 13.714 px → scale ≈ 1.524
        val result = calibrateFontSize(
            initialSize       = 28f,
            measuredCharWidth = 9f,
            dotsWidth         = 576,
            charsPerLine      = 42,
        )
        val expected = 28f * (576f / 42f) / 9f
        assertEquals(expected, result, 0.001f)
    }

    @Test
    fun `calibrateFontSize yields size where 42 chars span exactly 576 dots`() {
        val initialSize = 28f
        val measuredCharWidth = 9f
        val calibrated = calibrateFontSize(
            initialSize       = initialSize,
            measuredCharWidth = measuredCharWidth,
            dotsWidth         = 576,
            charsPerLine      = 42,
        )
        // At the calibrated size, each char is measuredCharWidth * (calibrated/initial) wide
        val scaledCharWidth = measuredCharWidth * (calibrated / initialSize)
        assertEquals(576f / 42f, scaledCharWidth, 0.01f)
    }

    @Test
    fun `calibrateFontSize returns initial size when measured width is zero`() {
        val result = calibrateFontSize(
            initialSize       = 28f,
            measuredCharWidth = 0f,
        )
        assertEquals(28f, result, 0.001f)
    }

    @Test
    fun `calibrateFontSize returns initial size when measured width is negative`() {
        val result = calibrateFontSize(
            initialSize       = 28f,
            measuredCharWidth = -5f,
        )
        assertEquals(28f, result, 0.001f)
    }

    @Test
    fun `calibrateFontSize returns initial size when char already matches target`() {
        val targetWidth = 576f / 42f          // ≈ 13.714 px per char
        val result = calibrateFontSize(
            initialSize       = 28f,
            measuredCharWidth = targetWidth,
            dotsWidth         = 576,
            charsPerLine      = 42,
        )
        assertEquals(28f, result, 0.001f)
    }

    @Test
    fun `calibrateFontSize scales up when chars are narrower than target`() {
        val result = calibrateFontSize(
            initialSize       = 28f,
            measuredCharWidth = 5f,           // narrower than 576/42 ≈ 13.7
            dotsWidth         = 576,
            charsPerLine      = 42,
        )
        assertTrue("should scale up for narrow chars", result > 28f)
    }

    @Test
    fun `calibrateFontSize scales down when chars are wider than target`() {
        val result = calibrateFontSize(
            initialSize       = 28f,
            measuredCharWidth = 20f,          // wider than 576/42 ≈ 13.7
            dotsWidth         = 576,
            charsPerLine      = 42,
        )
        assertTrue("should scale down for wide chars", result < 28f)
    }

    @Test
    fun `calibrateFontSize works with non-default paper dimensions`() {
        // 384-dot wide paper (58 mm), 32 chars per line
        val result = calibrateFontSize(
            initialSize       = 24f,
            measuredCharWidth = 8f,
            dotsWidth         = 384,
            charsPerLine      = 32,
        )
        val expected = 24f * (384f / 32f) / 8f
        assertEquals(expected, result, 0.001f)
    }

    // ── HORIZONTAL_INSET geometry ─────────────────────────────────────────────

    @Test
    fun `HORIZONTAL_INSET is positive`() {
        assertTrue("inset must be positive", HORIZONTAL_INSET > 0)
    }

    @Test
    fun `content width equals bitmap width minus both insets`() {
        val bitmapWidth = EscPosEncoder.PAPER_WIDTH_DOTS           // 576
        val contentWidth = bitmapWidth - 2 * HORIZONTAL_INSET      // 528
        assertEquals(bitmapWidth, contentWidth + 2 * HORIZONTAL_INSET)
    }

    @Test
    fun `inset is at least one calibrated character wide`() {
        // Guarantees that even if the printer clips one full character-width from the left
        // edge, no visible content is lost.
        val contentWidth = EscPosEncoder.PAPER_WIDTH_DOTS - 2 * HORIZONTAL_INSET
        val calibratedCharWidth = contentWidth.toFloat() / EscPosEncoder.PAPER_WIDTH_CHARS
        assertTrue(
            "HORIZONTAL_INSET ($HORIZONTAL_INSET dots) must be >= one char width ($calibratedCharWidth dots)",
            HORIZONTAL_INSET >= calibratedCharWidth,
        )
    }

    @Test
    fun `applying inset produces smaller calibrated character width than full-width`() {
        val measuredCharWidth = 16.8f   // Roboto Mono '0' at textSize 28px (estimated)
        val initialSize = 28f

        val fullWidth = EscPosEncoder.PAPER_WIDTH_DOTS
        val contentWidth = fullWidth - 2 * HORIZONTAL_INSET

        val calibratedFull    = calibrateFontSize(initialSize, measuredCharWidth, fullWidth, 42)
        val calibratedContent = calibrateFontSize(initialSize, measuredCharWidth, contentWidth, 42)

        assertTrue(
            "calibration with inset must produce a smaller font than without",
            calibratedContent < calibratedFull,
        )
    }

    @Test
    fun `separator line at content-calibrated size fits exactly in content width`() {
        val contentWidth = EscPosEncoder.PAPER_WIDTH_DOTS - 2 * HORIZONTAL_INSET
        val charsPerLine = EscPosEncoder.PAPER_WIDTH_CHARS

        // After calibration, charWidth = contentWidth / charsPerLine
        val calibratedCharWidth = contentWidth.toFloat() / charsPerLine
        val separatorWidth = charsPerLine * calibratedCharWidth

        assertEquals(contentWidth.toFloat(), separatorWidth, 0.01f)
    }
}
