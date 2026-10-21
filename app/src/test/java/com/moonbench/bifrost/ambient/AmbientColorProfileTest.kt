package com.moonbench.bifrost.ambient

import org.junit.Assert.*
import org.junit.Test

class AmbientColorProfileTest {
    private fun rgb(red: Int, green: Int, blue: Int) = AmbientSampling.rgb(red, green, blue)
    private fun r(color: Int) = color ushr 16 and 255
    private fun g(color: Int) = color ushr 8 and 255
    private fun b(color: Int) = color and 255

    @Test fun colorfulContentSurvivesDarkBackgroundAndWhiteHud() {
        val pixels = IntArray(100) { when (it) {
            in 0..19 -> rgb(220, 25, 15)
            in 20..79 -> rgb(10, 10, 10)
            else -> rgb(255, 255, 255)
        } }
        val output = AmbientColorProfile.representative(pixels)
        assertTrue("Red scene identity was washed out", r(output) > g(output) * 3 && r(output) > b(output) * 3)
        assertTrue("Colorful scene stayed dim", r(output) > 200)
    }

    @Test fun valueAndSaturationLiftRetainHueWithoutChannelClipping() {
        val output = AmbientColorProfile.representative(IntArray(16) { rgb(100, 70, 40) })
        assertTrue(r(output) > 100)
        assertTrue(r(output) > g(output) && g(output) > b(output))
        val ratio = (g(output) - b(output)).toDouble() / (r(output) - b(output))
        assertEquals(0.5, ratio, 0.02)
        assertTrue((r(output) - b(output)).toDouble() / r(output) > 0.6)
    }

    @Test fun blackRemainsZeroAndNeutralScenesDoNotAcquireAnInventedHue() {
        for (value in listOf(0, 1, 16, 80, 255)) {
            val output = AmbientColorProfile.representative(IntArray(100) { rgb(value, value, value) })
            assertEquals(r(output), g(output)); assertEquals(g(output), b(output))
            if (value == 0) assertEquals(rgb(0, 0, 0), output)
            else assertTrue(r(output) >= value)
        }
    }

    @Test fun tiedHighlightsAreIndependentOfScanOrderAndKeepBothHues() {
        val pixels = IntArray(100) { if (it < 50) rgb(255, 0, 0) else rgb(0, 0, 255) }
        val output = AmbientColorProfile.representative(pixels)
        assertEquals(output, AmbientColorProfile.representative(pixels.reversedArray()))
        assertEquals(rgb(146, 0, 146), output)
    }

    @Test fun darkColorNoiseCannotReplaceBrightNeutralContent() {
        val pixels = IntArray(100) { if (it < 10) rgb(1, 0, 0) else rgb(220, 220, 220) }
        val output = AmbientColorProfile.representative(pixels)
        assertEquals(r(output), g(output)); assertEquals(g(output), b(output))
        assertTrue(r(output) >= 220)
    }

    @Test fun pureColorsAndRedMagentaBoundaryRemainCorrect() {
        for (color in listOf(rgb(255, 0, 0), rgb(0, 255, 0), rgb(0, 0, 255), rgb(255, 0, 255)))
            assertEquals(color, AmbientColorProfile.representative(IntArray(8) { color }))
        val pink = AmbientColorProfile.representative(IntArray(8) { rgb(180, 20, 120) })
        assertTrue(r(pink) > b(pink) && b(pink) > g(pink))
    }
}
