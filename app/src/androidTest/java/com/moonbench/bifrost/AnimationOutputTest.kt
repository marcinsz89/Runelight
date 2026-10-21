package com.moonbench.bifrost

import android.graphics.Color
import android.os.Binder
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moonbench.bifrost.animations.*
import com.moonbench.bifrost.tools.LedController
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** Real animation timers and driver serialization; a local Binder never touches LEDs. */
@RunWith(AndroidJUnit4::class)
class AnimationOutputTest {
    @Test fun rejectedOutputIsReportedAndClosedDriversCannotReportIntoAnotherSession() {
        val rejected = LedController(object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int) = false
        })
        val failures = mutableListOf<Boolean>()
        rejected.onOutputResult = { failures.add(it) }
        rejected.setLedColor(255, 0, 0)
        assertEquals(listOf(false), failures)
        rejected.shutdown()
        rejected.setLedColor(0, 255, 0)
        assertEquals(listOf(false), failures)

        val output = RecordingBinder()
        val accepted = LedController(output)
        val results = mutableListOf<Boolean>()
        accepted.onOutputResult = { results.add(it) }
        accepted.setLedColor(0, 0, 255)
        accepted.setLedColor(0, 0, 255)
        assertEquals(listOf(true, true), results)
        assertEquals("An unchanged accepted frame should remain idle", 1, output.frames.size)
        accepted.shutdown()
    }

    private class RecordingBinder : Binder() {
        val frames = CopyOnWriteArrayList<List<Int>>()
        private val command = Regex("echo [12]-(\\d+):(\\d+):(\\d+):\\d+ > /sys/class/sn3112[lr]/led/brightness")

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            val text = data.createStringArray()?.firstOrNull() ?: return false
            val colors = command.findAll(text).map { match ->
                (match.groupValues[1].toInt() shl 16) or (match.groupValues[2].toInt() shl 8) or match.groupValues[3].toInt()
            }.toList()
            frames.add(colors)
            return true
        }
    }

    @Test fun animatedModesProduceChangingFramesAndStopCancelsTheirTimers() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (mode in listOf(LedAnimationType.RAINBOW, LedAnimationType.BREATH, LedAnimationType.PULSE)) {
            val output = RecordingBinder()
            val controller = LedController(output)
            controller.setSideBrightness(50, 25)
            lateinit var animation: LedAnimation
            instrumentation.runOnMainSync {
                animation = when (mode) {
                    LedAnimationType.RAINBOW -> RainbowAnimation(controller)
                    LedAnimationType.BREATH -> BreathAnimation(controller, Color.RED, Color.BLUE)
                    else -> PulseAnimation(controller, Color.RED, Color.BLUE)
                }
                animation.setTargetBrightness(255)
                animation.setSpeed(1f)
                animation.start()
            }
            lateinit var runningFrames: List<List<Int>>
            try {
                Thread.sleep(1200)
                runningFrames = output.frames.toList()
            } finally {
                instrumentation.runOnMainSync { animation.stop() }
            }
            assertTrue("$mode never changed output", runningFrames.distinct().size >= 3 || (mode == LedAnimationType.PULSE && runningFrames.distinct().size >= 2))
            assertTrue("$mode lost a zone", output.frames.all { it.size == 4 })
            assertEquals("$mode did not stop at black", listOf(0, 0, 0, 0), output.frames.last())
            val stoppedCount = output.frames.size
            Thread.sleep(100)
            assertEquals("$mode kept submitting after Stop", stoppedCount, output.frames.size)
        }
    }

    @Test fun staticSettlesWithoutRepeatedSubmissionsAndSideLevelsRemainIndependent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = RecordingBinder()
        val controller = LedController(output)
        controller.setSideBrightness(50, 25)
        lateinit var animation: StaticAnimation
        instrumentation.runOnMainSync {
            animation = StaticAnimation(controller, Color.RED, Color.BLUE)
            animation.start()
        }
        try {
            Thread.sleep(180)
            assertEquals(listOf(0x800000, 0x800000, 0x000040, 0x000040), output.frames.last())
            instrumentation.runOnMainSync { assertFalse("Solid still schedules work after settling", animation.hasPendingFrame) }
            val settledCount = output.frames.size
            Thread.sleep(150)
            assertEquals(settledCount, output.frames.size)
            instrumentation.runOnMainSync { controller.setSideBrightness(leftPercent = 0) }
            assertEquals(listOf(0, 0, 0x000040, 0x000040), output.frames.last())
        } finally {
            instrumentation.runOnMainSync { animation.stop() }
        }
    }

    @Test fun stoppedDriverCannotOverwriteAReplacementSession() {
        val output = RecordingBinder()
        val old = LedController(output)
        old.setLedColor(255, 0, 0)
        old.shutdown()
        assertEquals(listOf(0, 0, 0, 0), output.frames.last())
        assertFalse(old.isAvailable)
        val replacement = LedController(output)
        replacement.setLedColor(0, 0, 255)
        val count = output.frames.size
        // Late effect, side-level, fade and repeated cleanup callbacks all lose ownership.
        old.setLedColorDual(255, 0, 0, 0, 255, 0)
        old.setSideBrightness(50, 50)
        old.setMasterScale(0f)
        old.resetFadeBaseline()
        old.shutdown()
        assertEquals(count, output.frames.size)
        assertEquals(listOf(Color.BLUE and 0xffffff, Color.BLUE and 0xffffff, Color.BLUE and 0xffffff, Color.BLUE and 0xffffff), output.frames.last())
        replacement.shutdown()
        assertEquals(listOf(0, 0, 0, 0), output.frames.last())
    }

    @Test fun solidWakesForEditsConvergesAtLowLevelsAndReturnsToIdle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = RecordingBinder()
        lateinit var animation: StaticAnimation
        instrumentation.runOnMainSync {
            animation = StaticAnimation(LedController(output), Color.BLACK, Color.BLACK)
            animation.setLerpStrength(0f)
            animation.start()
        }
        try {
            Thread.sleep(100)
            instrumentation.runOnMainSync {
                assertFalse(animation.hasPendingFrame)
                animation.setTargetColor(0x020103) // Alpha is irrelevant to LED RGB.
                animation.setTargetRightColor(0x010202)
                assertTrue(animation.hasPendingFrame)
            }
            Thread.sleep(250)
            assertEquals(listOf(0x020103, 0x020103, 0x010202, 0x010202), output.frames.last())
            instrumentation.runOnMainSync {
                assertFalse(animation.hasPendingFrame)
                animation.setTargetColor(Color.BLACK)
                animation.setTargetRightColor(Color.BLACK)
            }
            Thread.sleep(250)
            assertEquals(listOf(0, 0, 0, 0), output.frames.last())
            instrumentation.runOnMainSync { assertFalse(animation.hasPendingFrame) }
        } finally {
            instrumentation.runOnMainSync { animation.stop() }
        }
    }

    @Test fun allPlainEffectHandoffsHoldOutputUntilTheNextFrameAndExplicitStopClearsIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val modes = listOf(LedAnimationType.STATIC, LedAnimationType.RAINBOW, LedAnimationType.BREATH, LedAnimationType.PULSE)
        fun create(mode: LedAnimationType, driver: LedController): LedAnimation = when (mode) {
            LedAnimationType.STATIC -> StaticAnimation(driver, Color.GREEN, Color.BLUE)
            LedAnimationType.RAINBOW -> RainbowAnimation(driver)
            LedAnimationType.BREATH -> BreathAnimation(driver, Color.GREEN, Color.BLUE)
            else -> PulseAnimation(driver, Color.GREEN, Color.BLUE)
        }
        for (from in modes) for (to in modes) {
            val output = RecordingBinder()
            val driver = LedController(output)
            lateinit var outgoing: LedAnimation
            var incoming: LedAnimation? = null
            instrumentation.runOnMainSync { outgoing = create(from, driver); outgoing.start() }
            instrumentation.waitForIdleSync()
            val before = output.frames.size
            assertTrue("$from never supplied an initial frame", before > 0)
            try {
                instrumentation.runOnMainSync {
                    outgoing.stopForHandoff()
                    assertEquals("$from cleared output during handoff", before, output.frames.size)
                    val next = create(to, driver)
                    incoming = next
                    next.start()
                }
                instrumentation.waitForIdleSync()
                assertTrue("$from -> $to inserted an off frame", output.frames.all { frame -> frame.any { it != 0 } })
            } finally {
                instrumentation.runOnMainSync { outgoing.stopForHandoff(); incoming?.stop() ?: driver.shutdown() }
            }
            assertEquals(listOf(0, 0, 0, 0), output.frames.last())
        }
    }
}
