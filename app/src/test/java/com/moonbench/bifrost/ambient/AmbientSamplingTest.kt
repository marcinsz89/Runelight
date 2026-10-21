package com.moonbench.bifrost.ambient

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class AmbientSamplingTest {
    private val black = AmbientSampling.rgb(0, 0, 0)
    private val red = AmbientSampling.rgb(255, 0, 0)
    private val blue = AmbientSampling.rgb(0, 0, 255)
    private val gray = AmbientSampling.rgb(80, 80, 80)

    @Test fun screenHalvesKeepIndependentSidesAndNeutralColors() {
        val pixels = IntArray(64 * 36) { if (it % 64 < 32) gray else AmbientSampling.rgb(255, 0, 255) }
        assertEquals(AmbientColors(AmbientSampling.rgb(101, 101, 101), AmbientSampling.rgb(255, 0, 255)), AmbientSampling.sample(pixels, 64, 36, AmbientSamplingMode.HALVES))
    }

    @Test fun fullScreenUsesOneAverageBeforeIndependentOutputBrightness() {
        val pixels = IntArray(64 * 36) { if (it % 64 < 32) red else blue }
        val purple = AmbientSampling.rgb(146, 0, 146)
        assertEquals(AmbientColors(purple, purple), AmbientSampling.sample(pixels, 64, 36, AmbientSamplingMode.FULL_SCREEN))
        assertEquals(AmbientSamplingMode.EDGES, AmbientSamplingMode.FULL_SCREEN.next())
    }

    @Test fun edgesAndHalvesSampleDifferentContentRegions() {
        val pixels = IntArray(64 * 36) { when (it % 64) { in 0..8 -> red; in 55..63 -> blue; else -> AmbientSampling.rgb(0, 255, 0) } }
        assertEquals(AmbientColors(red, blue), AmbientSampling.sample(pixels, 64, 36, AmbientSamplingMode.EDGES))
        val halves = AmbientSampling.sample(pixels, 64, 36, AmbientSamplingMode.HALVES)
        assertTrue((halves.left ushr 8 and 255) > (halves.left ushr 16 and 255))
        assertTrue((halves.right ushr 8 and 255) > (halves.right and 255))
    }

    @Test fun exactLetterboxBarsAreExcludedButBlackHalfAndAllBlackStayBlack() {
        val letterbox = IntArray(64 * 36) {
            if (it / 64 < 4 || it / 64 >= 32) black else if (it % 64 < 32) red else blue
        }
        assertEquals(AmbientColors(red, blue), AmbientSampling.sample(letterbox, 64, 36, AmbientSamplingMode.HALVES))
        val split = IntArray(64 * 36) { if (it % 64 < 32) black else blue }
        assertEquals(AmbientColors(black, blue), AmbientSampling.sample(split, 64, 36, AmbientSamplingMode.EDGES))
        for (mode in AmbientSamplingMode.entries) assertEquals(AmbientColors(black, black),
            AmbientSampling.sample(IntArray(64 * 36) { black }, 64, 36, mode))
    }

    @Test fun aThinSystemGestureBorderDoesNotLightAnOtherwiseBlackFrame() {
        val pixels = IntArray(64 * 36) {
            if (it % 64 == 63 || it / 64 == 0) gray else black
        }
        for (mode in AmbientSamplingMode.entries)
            assertEquals(AmbientColors(black, black), AmbientSampling.sample(pixels, 64, 36, mode))
    }

    @Test fun rgbaRespectsCropRowPaddingPixelStrideAndUnsignedChannels() {
        val buffer = ByteBuffer.allocate(4 * 24)
        buffer.put(24 + 4, 255.toByte()); buffer.put(24 + 5, 128.toByte()); buffer.put(24 + 6, 0)
        buffer.put(24 + 8, 0); buffer.put(24 + 9, 0); buffer.put(24 + 10, 255.toByte())
        val result = AmbientSampling.rgba(buffer, 24, 4, 1, 1, 2, 1)
        assertArrayEquals(intArrayOf(AmbientSampling.rgb(255, 128, 0), blue), result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidStridesFailBeforeReadingOutsideBuffer() {
        AmbientSampling.rgba(ByteBuffer.allocate(8), 24, 4, 1, 1, 2, 1)
    }

    @Test fun heldTargetConvergesWithoutAnotherCaptureAndBlackBecomesExactlyZero() {
        val smoother = AmbientSmoother()
        smoother.target(AmbientColors(red, blue), 0)
        for (time in 0L..2000L step 33L) smoother.frame(time)
        assertEquals(AmbientColors(red, blue), smoother.frame(2100))
        assertTrue(smoother.settled)
        smoother.target(AmbientColors(black, black), 2100)
        for (time in 2100L..4000L step 33L) smoother.frame(time)
        assertEquals(AmbientColors(black, black), smoother.frame(4100))
        assertTrue(smoother.settled)
    }

    @Test fun aTargetAfterLongIdleStartsASmoothTransition() {
        val smoother = AmbientSmoother()
        smoother.frame(0)
        smoother.target(AmbientColors(red, blue), 60_000)
        val first = smoother.frame(60_033).left ushr 16 and 255
        assertTrue("An idle clock must not make the next target snap instantly", first in 1 until 255)
    }

    @Test fun responseUsesElapsedTimeRatherThanCaptureCount() {
        fun output(step: Long): Int {
            val smoother = AmbientSmoother()
            smoother.frame(0)
            smoother.target(AmbientColors(red, blue), 0)
            for (time in step..330L step step) smoother.frame(time)
            return smoother.frame(330).left ushr 16 and 255
        }
        assertEquals(output(33), output(110))
    }

    @Test fun sceneChangeReachesMostOfItsColorWithin100msAndStillFadesToExactBlack() {
        val smoother = AmbientSmoother()
        smoother.target(AmbientColors(red, blue), 0)
        assertTrue((smoother.frame(16).left ushr 16 and 255) in 1 until 255)
        val bright = smoother.frame(100)
        assertTrue((bright.left ushr 16 and 255) >= 240)
        assertTrue((bright.right and 255) >= 240)
        assertEquals(0, bright.left and 0xffff)
        assertEquals(0, bright.right and 0xffff00)
        smoother.target(AmbientColors(black, black), 100)
        val fading = smoother.frame(200)
        assertTrue((fading.left ushr 16 and 255) <= 15)
        assertTrue((fading.right and 255) <= 15)
        assertEquals(AmbientColors(black, black), smoother.frame(400))
        assertTrue(smoother.settled)
    }
}
