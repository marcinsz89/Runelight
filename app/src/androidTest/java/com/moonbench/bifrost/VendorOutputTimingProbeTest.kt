package com.moonbench.bifrost

import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moonbench.bifrost.tools.LedFrameState
import com.moonbench.bifrost.tools.LedController
import com.moonbench.bifrost.tools.VendorLedOutput
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit, bounded hardware timing experiment; skipped by ordinary test runs. */
@RunWith(AndroidJUnit4::class)
class VendorOutputTimingProbeTest {
    @Test fun measurePacedProductionWriterUnderCombinedLoad() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("runelightVendorTiming") == "true")
        val manager = Class.forName("android.os.ServiceManager")
        val binder = manager.getDeclaredMethod("getService", String::class.java).invoke(null, "PServerBinder") as IBinder
        val driver = LedController(binder, VendorLedOutput)
        val timings = CopyOnWriteArrayList<Pair<Long, Long>>()
        val failures = CopyOnWriteArrayList<Boolean>()
        val start = SystemClock.elapsedRealtime()
        try {
            repeat(1400) { index ->
                val offered = SystemClock.elapsedRealtime()
                driver.onOutputResult = { accepted ->
                    if (accepted) timings.add(offered - start to SystemClock.elapsedRealtime() - offered)
                    else failures.add(false)
                }
                val level = if (index % 2 == 0) 24 else 48
                driver.setLedColorDual(level, 0, 0, 0, 0, level)
                SystemClock.sleep((start + (index + 1) * 1000 / 70 - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            }
            val done = CountDownLatch(1)
            driver.onOutputResult = { if (it) done.countDown() else failures.add(false) }
            val offered = SystemClock.elapsedRealtime()
            driver.setLedColor(0, 24, 0)
            assertTrue(done.await(3, TimeUnit.SECONDS))
            val result = JSONObject().put("offered_hz", 70).put("duration_ms", offered - start)
                .put("completed_frames", timings.size).put("failures", failures.size)
                .put("final_frame_ms", SystemClock.elapsedRealtime() - offered)
            val samples = JSONArray()
            timings.forEach { samples.put(JSONArray().put(it.first).put(it.second)) }
            result.put("offered_at_and_completed_after_ms", samples)
            File(instrumentation.targetContext.filesDir, "paced-output-timing.json").writeText(result.toString(2))
            assertTrue(failures.isEmpty())
        } finally { driver.shutdown(); SystemClock.sleep(150) }
    }

    @Test fun measureShellAndFourZoneCompletion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("runelightVendorTiming") == "true")
        val manager = Class.forName("android.os.ServiceManager")
        val binder = manager.getDeclaredMethod("getService", String::class.java)
            .invoke(null, "PServerBinder") as IBinder
        fun execute(command: String, flags: Int = 0): Pair<Long, String> {
            val data = Parcel.obtain(); val reply = Parcel.obtain()
            try {
                data.writeStringArray(arrayOf(command, "1"))
                val start = SystemClock.elapsedRealtimeNanos()
                check(binder.transact(0, data, reply, flags))
                val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
                return elapsed to if (flags == 0) (reply.createByteArray()?.toString(Charsets.UTF_8) ?: "") else ""
            } finally { data.recycle(); reply.recycle() }
        }
        fun frame(level: Int): String = LedFrameState().apply {
            update(LedFrameState.Color(level, 0, 0), LedFrameState.Color(0, 0, level),
                booleanArrayOf(true, true, true, true))
        }.pending()!!.command
        val result = JSONObject()
        val samples = JSONArray()
        try {
            result.put("initial_barrier_ms", execute("printf RL_TIMING").first)
            repeat(20) { index -> samples.put(execute(frame(if (index % 2 == 0) 24 else 48)).first) }
            result.put("four_zone_sync_ms", samples)
            // The marker executes in the SAME one-way stream as the LED frames.
            // A synchronous transaction can run on another Binder thread and
            // must not be mistaken for a fence for preceding one-way work.
            for (rate in listOf(40, 70)) {
                val path = "/data/local/tmp/rl_led_${rate}_${android.os.Process.myPid()}"
                result.put("ordered_marker_$rate", path)
                execute("date +%s%N > $path", IBinder.FLAG_ONEWAY)
                val start = SystemClock.elapsedRealtime()
                repeat(rate) { index ->
                    execute(frame(if (index % 2 == 0) 24 else 48), IBinder.FLAG_ONEWAY)
                    SystemClock.sleep((start + (index + 1) * 1000 / rate - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                }
                execute("date +%s%N >> $path;chmod 644 $path", IBinder.FLAG_ONEWAY)
            }
        } finally {
            execute(frame(0), IBinder.FLAG_ONEWAY)
            val path = "/data/local/tmp/rl_led_done_${android.os.Process.myPid()}"
            execute("date +%s%N > $path;chmod 644 $path", IBinder.FLAG_ONEWAY)
            result.put("ordered_done_marker", path)
            File(instrumentation.targetContext.filesDir, "vendor-output-timing.json").writeText(result.toString(2))
        }
        assertTrue(samples.length() == 20)
    }
}
