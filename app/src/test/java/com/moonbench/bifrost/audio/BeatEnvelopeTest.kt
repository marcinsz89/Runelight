package com.moonbench.bifrost.audio

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class BeatEnvelopeTest {
    private class Signal {
        val envelope = AudioEnvelope().apply { setMode(SoundMode.BEAT) }
        private var offset = 0
        fun frame(bass: Double = 0.0, melody: Double = 0.04): Float {
            val samples = ShortArray(400) {
                val time = offset++ / 16000.0
                ((bass * sin(2 * PI * 80 * time) + melody * sin(2 * PI * 880 * time)) * 32767)
                    .toInt().coerceIn(-32768, 32767).toShort()
            }
            envelope.samples(samples, samples.size)
            return envelope.frame(offset / 16L)
        }
    }

    @Test fun quietMelodyStaysDarkWhileBassHitsLightUpAndReleaseQuickly() {
        val signal = Signal()
        repeat(40) { assertTrue(signal.frame() < 0.01f) }
        val peak = (0 until 4).maxOf { signal.frame(bass = 0.22) }
        assertTrue("Hit should be bright, got $peak", peak > 0.8f)
        var tail = 1f
        repeat(16) { tail = signal.frame() }
        assertTrue("Melody between hits should be dark, got $tail", tail < 0.01f)
    }

    @Test fun repeatedHitsDoNotRaiseTheThresholdUntilAllBeatsDisappear() {
        val signal = Signal()
        repeat(40) { signal.frame() }
        repeat(60) { beat ->
            val peak = (0 until 3).maxOf { signal.frame(bass = 0.22) }
            assertTrue("Beat $beat was suppressed: $peak", peak > 0.65f)
            repeat(17) { signal.frame() }
        }
    }

    @Test fun bassHitsAreStrongerThanAnEqualAmplitudeMelodyJump() {
        val bass = Signal()
        val treble = Signal()
        repeat(40) { bass.frame(); treble.frame() }
        val bassPeak = (0 until 3).maxOf { bass.frame(bass = 0.22) }
        val treblePeak = (0 until 3).maxOf { treble.frame(melody = 0.22) }
        assertTrue("Bass $bassPeak should dominate treble $treblePeak", bassPeak > treblePeak * 1.5f)
    }

    @Test fun sustainedLoudAudioSettlesDarkInsteadOfHoldingTheLedsOn() {
        val signal = Signal()
        repeat(40) { signal.frame() }
        repeat(160) { signal.frame(bass = 0.25, melody = 0.1) }
        assertEquals(0f, signal.frame(bass = 0.25, melody = 0.1), 0f)
    }

    @Test fun silenceReachesExactBlackAfterABeat() {
        val signal = Signal()
        repeat(20) { signal.frame() }
        repeat(4) { signal.frame(bass = 0.25) }
        repeat(40) { signal.frame(melody = 0.0) }
        assertEquals(0f, signal.frame(melody = 0.0), 0f)
    }

    @Test fun changingModeResetsHeldTargetsAndCanRestoreTheOriginalLevelResponse() {
        val signal = Signal()
        repeat(40) { signal.frame() }
        signal.envelope.setMode(SoundMode.LEVEL)
        repeat(40) { signal.frame() }
        assertTrue(signal.frame() > 0.1f)
        signal.envelope.setMode(SoundMode.BEAT)
        assertEquals(0f, signal.envelope.frame(2100), 0f)
        repeat(40) { signal.frame() }
        assertTrue(signal.frame() < 0.01f)
        signal.envelope.setMode(SoundMode.OFF)
        assertEquals(0f, signal.frame(bass = 0.8), 0f)
    }

    @Test fun savedToggleMigrationAndCyclePreserveExplicitChoices() {
        assertEquals(SoundMode.OFF, SoundMode.fromStoredName(null))
        assertEquals(SoundMode.LEVEL, SoundMode.fromStoredName(null, legacyEnabled = true))
        assertEquals(SoundMode.OFF, SoundMode.fromStoredName("OFF", legacyEnabled = true))
        assertEquals(SoundMode.BEAT, SoundMode.fromStoredName("BEAT", legacyEnabled = false))
        assertEquals(SoundMode.OFF, SoundMode.OFF.next().next().next())
        assertEquals(SoundMode.LEVEL, SoundMode.OFF.next())
        assertEquals(SoundMode.BEAT, SoundMode.LEVEL.next())
    }
}
