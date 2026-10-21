package com.moonbench.bifrost

import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.Parcel
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moonbench.bifrost.tools.LatestValueWriter
import com.moonbench.bifrost.tools.LedController
import com.moonbench.bifrost.tools.LedOutputDispatcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A deliberately blocked local Binder exercises the production queue, never hardware. */
@RunWith(AndroidJUnit4::class)
class LedOutputBackpressureTest {
    private class Output(private val blockAt: Int) : Binder() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val frames = CopyOnWriteArrayList<List<Int>>()
        val flags = CopyOnWriteArrayList<Int>()
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            val command = data.createStringArray()!![0]
            this.flags.add(flags)
            frames.add(Regex("echo [12]-(\\d+):(\\d+):(\\d+):\\d+").findAll(command).map {
                (it.groupValues[1].toInt() shl 16) or (it.groupValues[2].toInt() shl 8) or it.groupValues[3].toInt()
            }.toList())
            if (frames.size == blockAt) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
            return true
        }
    }
    private class Queue : LedOutputDispatcher, AutoCloseable {
        private val thread = HandlerThread("BlockedLedTest").apply { start() }
        private val handler = Handler(thread.looper)
        private val writer = LatestValueWriter<() -> Boolean>(SystemClock::elapsedRealtime,
            { delay, task -> handler.postDelayed(task, delay) }, { it() })
        override fun submit(write: () -> Boolean, completed: (Boolean) -> Unit) = writer.offer(write, completed)
        override fun close() { thread.quitSafely(); thread.join(2000) }
    }
    private fun await(check: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 3000
        while (!check() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(5)
        assertTrue(check())
    }

    @Test fun blockedOutputCoalescesAndStopThenReplacementRejectsClosedDriverCallbacks() {
        val binder = Output(1)
        Queue().use { queue ->
            val old = LedController(binder, queue)
            val oldResults = CopyOnWriteArrayList<Boolean>()
            old.onOutputResult = { oldResults.add(it) }
            old.setLedColorDual(255, 0, 0, 0, 0, 255)
            assertTrue(binder.entered.await(2, TimeUnit.SECONDS))
            val replacement = LedController(binder, queue)
            try {
                repeat(100) { old.setMasterScale(it / 100f) }
                old.shutdown() // Pending black can be superseded only by the later explicit Start.
                val accepted = CountDownLatch(1)
                replacement.onOutputResult = { if (it) accepted.countDown() }
                replacement.setLedColorDual(0, 255, 255, 255, 0, 255)
                old.setLedColor(255, 255, 255)
                assertEquals(1, binder.frames.size)
                assertTrue(oldResults.isEmpty())
                binder.release.countDown()
                assertTrue(accepted.await(3, TimeUnit.SECONDS))
                assertEquals(2, binder.frames.size)
                assertEquals(listOf(0x00ffff, 0x00ffff, 0xff00ff, 0xff00ff), binder.frames.last())
                assertTrue(oldResults.isEmpty())
                assertTrue(binder.flags.all { it == 0 })
                replacement.shutdown()
                await { binder.frames.last() == listOf(0, 0, 0, 0) }
            } finally { binder.release.countDown(); old.shutdown(); replacement.shutdown() }
        }
    }

    @Test fun returningToPreviouslyWrittenColorWhileAnotherFrameIsInFlightIsNotLost() {
        val binder = Output(2)
        Queue().use { queue ->
            val driver = LedController(binder, queue)
            val results = CopyOnWriteArrayList<Boolean>()
            driver.onOutputResult = { results.add(it) }
            try {
                driver.setLedColor(255, 0, 0)
                await { results.size == 1 }
                driver.setLedColor(0, 0, 255)
                assertTrue(binder.entered.await(2, TimeUnit.SECONDS))
                driver.setLedColor(255, 0, 0)
                assertEquals(1, results.size) // Previously written red cannot acknowledge this new request.
                binder.release.countDown()
                await { results.size == 3 }
                assertEquals(listOf(0xff0000, 0xff0000, 0xff0000, 0xff0000), binder.frames.last())
                assertEquals(3, binder.frames.size)
            } finally { binder.release.countDown(); driver.shutdown() }
        }
    }

    @Test fun newEffectWantingTheSamePendingFrameGetsItsCompletion() {
        val binder = Output(1)
        Queue().use { queue ->
            val driver = LedController(binder, queue)
            val oldResults = CopyOnWriteArrayList<Boolean>()
            val newResults = CopyOnWriteArrayList<Boolean>()
            try {
                driver.onOutputResult = { oldResults.add(it) }
                driver.setLedColor(0, 255, 0)
                assertTrue(binder.entered.await(2, TimeUnit.SECONDS))
                driver.onOutputResult = { newResults.add(it) }
                driver.setLedColor(0, 255, 0)
                assertTrue(newResults.isEmpty())
                binder.release.countDown()
                await { newResults.size == 1 }
                assertTrue(oldResults.isEmpty())
                assertEquals(listOf(true), newResults)
                assertEquals(1, binder.frames.size)
            } finally { binder.release.countDown(); driver.shutdown() }
        }
    }
}
