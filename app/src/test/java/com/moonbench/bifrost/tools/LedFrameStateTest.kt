package com.moonbench.bifrost.tools

import org.junit.Assert.*
import org.junit.Test

class LedFrameStateTest {
    private val all = booleanArrayOf(true, true, true, true)
    private val red = LedFrameState.Color(255, 0, 0)
    private val blue = LedFrameState.Color(0, 0, 255)

    @Test fun sideLevelsPreserveBothZonesAndOtherSideColor() {
        val frame = LedFrameState()
        frame.update(red, blue, all)
        frame.leftPercent = 0
        frame.rightPercent = 40
        assertEquals(listOf(red.copy(red = 0), red.copy(red = 0), blue.copy(blue = 102), blue.copy(blue = 102)), frame.pending()!!.colors)
        frame.leftPercent = 100
        assertEquals(listOf(red, red, blue.copy(blue = 102), blue.copy(blue = 102)), frame.pending()!!.colors)
    }

    @Test fun partialUpdatesKeepIndependentRawZoneValuesAcrossFades() {
        val frame = LedFrameState()
        frame.update(red, blue, all)
        val green = LedFrameState.Color(0, 255, 0)
        frame.update(green, green, booleanArrayOf(false, true, false, false))
        frame.masterScale = 0f
        assertTrue(frame.pending()!!.colors.all { it!!.red + it.green + it.blue == 0 })
        frame.masterScale = 1f
        assertEquals(listOf(red, green, blue, blue), frame.pending()!!.colors)
    }

    @Test fun unchangedOutputIsIdleAndRescalingDoesNotCompound() {
        val frame = LedFrameState()
        frame.update(red, blue, all)
        frame.leftPercent = 50
        val first = frame.pending()!!
        frame.markSubmitted(first)
        frame.update(red, blue, all)
        assertNull(frame.pending())
        frame.leftPercent = 100
        assertEquals(red, frame.pending()!!.colors[0])
    }

    @Test fun blackRemainsZeroAndBaselineResetCannotRestoreOldColors() {
        val frame = LedFrameState()
        frame.update(red, blue, all)
        frame.blackBaseline()
        frame.masterScale = 0.6f
        frame.leftPercent = 100
        frame.rightPercent = 30
        assertTrue(frame.pending()!!.colors.all { it!!.red + it.green + it.blue == 0 })
    }

    @Test fun failedSubmissionRemainsPendingAndRawBoundsAreClamped() {
        val frame = LedFrameState()
        frame.update(LedFrameState.Color(-5, 300, 100, 500), blue, all)
        val first = frame.pending()!!
        assertEquals(LedFrameState.Color(0, 255, 100, 255), first.colors[0])
        assertEquals(first, frame.pending())
        frame.markSubmitted(first)
        assertNull(frame.pending())
    }
}
