package com.moonbench.bifrost.tools

import org.junit.Assert.*
import org.junit.Test
import java.util.PriorityQueue

class LatestValueWriterTest {
    private class Clock {
        var now = 0L
        private val tasks = PriorityQueue<Pair<Long, () -> Unit>>(compareBy { it.first })
        fun schedule(delay: Long, task: () -> Unit) { tasks.add(now + delay to task) }
        fun next(): Boolean {
            val task = tasks.poll() ?: return false
            now = maxOf(now, task.first); task.second(); return true
        }
        val pending get() = tasks.size
    }

    @Test fun burstsKeepOnlyTheLatestFrameAndLeaveNoIdlePolling() {
        val clock = Clock(); val writes = mutableListOf<Pair<Long, Int>>()
        val completed = mutableListOf<Int>()
        val writer = LatestValueWriter<Int>({ clock.now }, clock::schedule, { writes.add(clock.now to it); true })
        repeat(100) { value -> writer.offer(value) { completed.add(value) } }
        assertEquals(1, clock.pending)
        assertTrue(clock.next())
        assertEquals(listOf(0L to 99), writes); assertEquals(listOf(99), completed)
        writer.offer(100) {}; writer.offer(101) {}
        assertTrue(clock.next())
        assertEquals(listOf(0L to 99, 40L to 101), writes)
        assertFalse(clock.next())
    }

    @Test fun slowWriteDoesNotBlockNewTargetsOrReplayIntermediateFrames() {
        val clock = Clock(); val writes = mutableListOf<Int>(); val completions = mutableListOf<Int>()
        lateinit var writer: LatestValueWriter<Int>
        writer = LatestValueWriter({ clock.now }, clock::schedule, { value ->
            writes.add(value)
            if (value == 1) {
                repeat(100) { index -> writer.offer(index + 2) { completions.add(index + 2) } }
                assertEquals(0, clock.pending) // No second writer while the first is in flight.
                clock.now += 200
            }
            true
        })
        writer.offer(1) { completions.add(1) }
        assertTrue(clock.next()); assertEquals(1, clock.pending)
        assertTrue(clock.next()); assertFalse(clock.next())
        assertEquals(listOf(1, 101), writes); assertEquals(listOf(1, 101), completions)
    }

    @Test fun stopReplacesQueuedColorsAndLaterStartSupersedesPendingStop() {
        val clock = Clock(); val writes = mutableListOf<String>()
        val writer = LatestValueWriter<String>({ clock.now }, clock::schedule, { writes.add(it); true })
        writer.offer("old color") {}; writer.offer("black Stop") {}
        clock.next(); assertEquals(listOf("black Stop"), writes)
        writer.offer("black Stop") {}; writer.offer("new Start") {}
        clock.next(); assertEquals(listOf("black Stop", "new Start"), writes)
        assertFalse(clock.next())
    }

    @Test fun writeFailureIsReportedOnceWithoutRetryAndNextExplicitOfferCanRun() {
        val clock = Clock(); val results = mutableListOf<Boolean>(); var attempts = 0
        val writer = LatestValueWriter<Int>({ clock.now }, clock::schedule, {
            attempts++; if (it == 1) error("transport failure") else true
        })
        writer.offer(1, results::add); clock.next()
        assertEquals(listOf(false), results); assertEquals(1, attempts); assertFalse(clock.next())
        writer.offer(2, results::add); clock.next()
        assertEquals(listOf(false, true), results); assertEquals(2, attempts); assertFalse(clock.next())
    }
}
