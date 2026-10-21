package com.moonbench.bifrost

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moonbench.bifrost.audio.PlaybackAudioCapture
import com.moonbench.bifrost.audio.PlaybackRecorder
import com.moonbench.bifrost.audio.SoundPhase
import com.moonbench.bifrost.audio.SoundMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PlaybackAudioCaptureTest {
    @Test fun changingResponseModeReusesTheRecorderAndDoesNotRestartCapture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val allocations = AtomicInteger()
        val starts = AtomicInteger()
        val closes = AtomicInteger()
        val states = CopyOnWriteArrayList<SoundPhase>()
        val levels = CopyOnWriteArrayList<Float>()
        lateinit var capture: PlaybackAudioCapture
        instrumentation.runOnMainSync {
            capture = PlaybackAudioCapture(instrumentation.targetContext, null, Handler(Looper.getMainLooper()),
                { levels.add(it) }, { states.add(it) }) {
                allocations.incrementAndGet()
                object : PlaybackRecorder {
                    override fun start() { starts.incrementAndGet() }
                    override fun read(buffer: ShortArray): Int { buffer.fill(2000); return 400 }
                    override fun close() { closes.incrementAndGet() }
                }
            }
            capture.start()
        }
        try {
            Thread.sleep(175)
            assertTrue(levels.last() > 0.1f)
            instrumentation.runOnMainSync { capture.setMode(SoundMode.BEAT) }
            Thread.sleep(175)
            assertEquals(0f, levels.last(), 0.01f)
            instrumentation.runOnMainSync { capture.setMode(SoundMode.LEVEL) }
            Thread.sleep(175)
            assertTrue(levels.last() > 0.1f)
            assertEquals(1, allocations.get()); assertEquals(1, starts.get()); assertEquals(0, closes.get())
            assertEquals(listOf(SoundPhase.STARTING, SoundPhase.ACTIVE), states.toList())
        } finally { instrumentation.runOnMainSync { capture.close() } }
        assertEquals(1, closes.get())
    }
    @Test fun failedReadReleasesOnceAndNeverRetriesOrPublishesLateLevels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val reads = AtomicInteger()
        val closes = AtomicInteger()
        val states = CopyOnWriteArrayList<SoundPhase>()
        val levels = CopyOnWriteArrayList<Float>()
        lateinit var capture: PlaybackAudioCapture
        instrumentation.runOnMainSync {
            capture = PlaybackAudioCapture(instrumentation.targetContext, null, Handler(Looper.getMainLooper()),
                { levels.add(it) }, { states.add(it) }) {
                object : PlaybackRecorder {
                    override fun start() {}
                    override fun read(buffer: ShortArray): Int { reads.incrementAndGet(); return -3 }
                    override fun close() { closes.incrementAndGet() }
                }
            }
            capture.start()
        }
        Thread.sleep(150)
        assertEquals(listOf(SoundPhase.STARTING, SoundPhase.FAILED), states.toList())
        assertEquals(1, reads.get()); assertEquals(1, closes.get())
        assertTrue(levels.isEmpty())
        instrumentation.runOnMainSync { capture.close(); capture.close() }
        Thread.sleep(75)
        assertEquals(1, reads.get()); assertEquals(1, closes.get())
    }

    @Test fun stoppingAReadingSessionCancelsPollsAndReleasesItsRecorder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val reads = AtomicInteger()
        val closes = AtomicInteger()
        val levels = CopyOnWriteArrayList<Float>()
        lateinit var capture: PlaybackAudioCapture
        instrumentation.runOnMainSync {
            capture = PlaybackAudioCapture(instrumentation.targetContext, null, Handler(Looper.getMainLooper()),
                { levels.add(it) }, {}) {
                object : PlaybackRecorder {
                    override fun start() {}
                    override fun read(buffer: ShortArray): Int { reads.incrementAndGet(); buffer.fill(2000); return buffer.size }
                    override fun close() { closes.incrementAndGet() }
                }
            }
            capture.start()
        }
        Thread.sleep(125)
        instrumentation.runOnMainSync { capture.close() }
        val before = reads.get(); val beforeLevels = levels.size
        Thread.sleep(100)
        assertTrue(before > 0); assertEquals(1, closes.get())
        assertEquals(before, reads.get()); assertEquals(beforeLevels, levels.size)
    }
}
