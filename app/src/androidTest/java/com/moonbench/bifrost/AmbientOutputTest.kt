package com.moonbench.bifrost

import android.app.Activity
import android.content.Intent
import android.os.Binder
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moonbench.bifrost.ambient.AmbientSamplingMode
import com.moonbench.bifrost.ambient.AmbientCapture
import com.moonbench.bifrost.ambient.NativeAmbientAnimation
import com.moonbench.bifrost.tools.LedController
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** Real timers, complete driver frames and a controllable capture lifetime. No LEDs. */
@RunWith(AndroidJUnit4::class)
class AmbientOutputTest {
    @Test fun heldFrameSamplingBrightnessAndStopDoNotRestartCaptureOrAcceptLateCallbacks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val frames = CopyOnWriteArrayList<List<Int>>()
        val binder = object : Binder() {
            val command = Regex("echo [12]-(\\d+):(\\d+):(\\d+):\\d+ > /sys/class/sn3112[lr]/led/brightness")
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                val text = data.createStringArray()?.firstOrNull() ?: return false
                frames.add(command.findAll(text).map {
                    (it.groupValues[1].toInt() shl 16) or (it.groupValues[2].toInt() shl 8) or it.groupValues[3].toInt()
                }.toList())
                return true
            }
        }
        val driver = LedController(binder)
        lateinit var animation: NativeAmbientAnimation
        lateinit var deliver: (IntArray, Int, Int) -> Unit
        lateinit var fail: () -> Unit
        var starts = 0
        var closes = 0
        var failures = 0
        val pattern = IntArray(64 * 36) { when (it % 64) {
            in 0..7 -> 0xffff0000.toInt()
            in 56..63 -> 0xff0000ff.toInt()
            else -> 0xff000000.toInt()
        } }
        instrumentation.runOnMainSync {
            animation = NativeAmbientAnimation(instrumentation.targetContext, driver, Activity.RESULT_OK,
                Intent(), AmbientSamplingMode.EDGES, { failures++ }) { frame, failure ->
                deliver = frame; fail = failure
                object : AmbientCapture {
                    override fun start(resultCode: Int, grant: Intent) { starts++ }
                    override fun close() { closes++ }
                }
            }
            animation.start()
            assertFalse(animation.hasRenderedFrame)
            deliver(pattern, 64, 36)
        }
        try {
            Thread.sleep(1100)
            assertEquals(listOf(0xff0000, 0xff0000, 0x0000ff, 0x0000ff), frames.last())
            val count = frames.size
            Thread.sleep(150)
            assertEquals("Settled Ambient must not keep submitting", count, frames.size)
            instrumentation.runOnMainSync { driver.setSideBrightness(50, 25); animation.setSamplingMode(AmbientSamplingMode.HALVES) }
            Thread.sleep(1100)
            assertEquals(listOf(0x800000, 0x800000, 0x000040, 0x000040), frames.last())
            assertEquals(1, starts)
            instrumentation.runOnMainSync { animation.setSamplingMode(AmbientSamplingMode.FULL_SCREEN) }
            Thread.sleep(1100)
            assertEquals(listOf(0x490049, 0x490049, 0x250025, 0x250025), frames.last())
            assertEquals(1, starts)
            instrumentation.runOnMainSync { deliver(IntArray(64 * 36) { 0xff000000.toInt() }, 64, 36) }
            Thread.sleep(1100)
            assertEquals(listOf(0, 0, 0, 0), frames.last())
            instrumentation.runOnMainSync {
                animation.stop()
                driver.shutdown()
                deliver(pattern, 64, 36)
                fail()
            }
            val stopped = frames.size
            Thread.sleep(150)
            assertEquals(stopped, frames.size)
            assertEquals(0, failures)
            assertEquals(1, closes)
        } finally {
            instrumentation.runOnMainSync { animation.stop(); driver.shutdown() }
        }
    }
}
