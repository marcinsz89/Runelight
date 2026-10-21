package com.moonbench.bifrost.audio

import com.moonbench.bifrost.animations.LedAnimationType
import org.junit.Assert.*
import org.junit.Test

class AudioEnvelopeTest {
    @Test fun breathingAndPulsePauseSoundWithoutChangingItsSavedPreference() {
        for (effect in listOf(LedAnimationType.BREATH, LedAnimationType.PULSE)) assertFalse(SoundPolicy.supports(effect))
        for (effect in listOf(LedAnimationType.STATIC, LedAnimationType.RAINBOW, LedAnimationType.AMBIENT)) assertTrue(SoundPolicy.supports(effect))
        assertFalse(SoundPolicy.supports(null))
    }
    @Test fun quietLoudAndSilentSamplesHaveDifferentStableLevels() {
        val envelope = AudioEnvelope()
        envelope.samples(ShortArray(400) { 1600 }, 400)
        var quiet = 0f
        for (time in 0L..1000L step 25L) quiet = envelope.frame(time)
        envelope.samples(ShortArray(400) { 4800 }, 400)
        var loud = 0f
        for (time in 1025L..2000L step 25L) loud = envelope.frame(time)
        assertTrue(quiet in 0.2f..0.4f)
        assertTrue(loud > quiet * 2)
        envelope.samples(ShortArray(400), 400)
        var silent = 1f
        for (time in 2025L..4000L step 25L) silent = envelope.frame(time)
        assertEquals(0f, silent, 0f)
    }
    @Test fun noiseFloorAndNegativeFullScaleAreSafe() {
        val envelope = AudioEnvelope()
        envelope.samples(ShortArray(400) { 30 }, 400)
        assertEquals(0f, envelope.frame(0), 0f)
        envelope.samples(ShortArray(400) { Short.MIN_VALUE }, 400)
        assertTrue(envelope.frame(25) in 0f..1f)
        assertEquals(1f, envelope.frame(1000), 0f)
    }
    @Test fun nonblockingZeroReadsAreBoundedButSilenceIsValidData() {
        val health = AudioReadHealth(100)
        assertTrue(health.healthy(0, 1000))
        assertFalse(health.healthy(0, 5100))
        assertTrue(health.healthy(400, 5200))
        assertTrue(health.healthy(400, 9000))
        assertFalse(health.healthy(-3, 9001))
    }
}
