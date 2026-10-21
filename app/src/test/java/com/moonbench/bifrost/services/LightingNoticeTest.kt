package com.moonbench.bifrost.services

import com.moonbench.bifrost.R
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.audio.SoundPhase
import com.moonbench.bifrost.audio.SoundState
import com.moonbench.bifrost.audio.SoundMode
import org.junit.Assert.*
import org.junit.Test

class LightingNoticeTest {
    @Test fun beatReactionIsNamedWithoutClaimingADifferentLightingEffect() {
        val notice = LightingNotice.from(LightingState(LightingPhase.RUNNING, LedAnimationType.AMBIENT),
            SoundState(SoundPhase.ACTIVE, true, SoundMode.BEAT))!!
        assertEquals(R.string.runelight_ambient, notice.title)
        assertEquals(R.string.runelight_notice_beat_active, notice.detail)
    }
    private fun running(effect: LedAnimationType = LedAnimationType.AMBIENT, sound: SoundPhase = SoundPhase.OFF) =
        LightingNotice.from(LightingState(LightingPhase.RUNNING, effect), SoundState(sound))!!

    @Test fun stoppedNeverLeavesAnActiveNoticeEvenWithStaleSound() {
        for (sound in SoundPhase.entries)
            assertNull(LightingNotice.from(LightingState(), SoundState(sound, true)))
    }

    @Test fun startingDoesNotClaimTheEffectOrSoundIsAlreadyRunning() {
        val runtime = LightingRuntime()
        val token = runtime.begin(LedAnimationType.RAINBOW)
        val notice = LightingNotice.from(runtime.state, SoundState(SoundPhase.ACTIVE))!!
        assertEquals(R.string.runelight_starting, notice.title)
        assertEquals(R.string.runelight_rainbow, notice.detail)
        assertTrue(notice.ongoing)
        runtime.frameAccepted(token)
        assertEquals(R.string.runelight_rainbow, LightingNotice.from(runtime.state, SoundState())!!.title)
    }

    @Test fun eachRetainedEffectHasItsPlainProductName() {
        val labels = mapOf(LedAnimationType.AMBIENT to R.string.runelight_ambient,
            LedAnimationType.STATIC to R.string.runelight_solid,
            LedAnimationType.RAINBOW to R.string.runelight_rainbow,
            LedAnimationType.BREATH to R.string.runelight_breathing,
            LedAnimationType.PULSE to R.string.runelight_pulse)
        for ((effect, label) in labels) {
            assertEquals(label, running(effect).title)
            assertTrue(running(effect).ongoing)
        }
    }

    @Test fun soundErrorsKeepTheBaseEffectActiveAndOfferTheApplicableRecovery() {
        val details = mapOf(SoundPhase.NEEDS_PERMISSION to R.string.runelight_notice_sound_permission,
            SoundPhase.NEEDS_CONSENT to R.string.runelight_notice_sound_consent,
            SoundPhase.FAILED to R.string.runelight_notice_sound_failed)
        for ((sound, detail) in details) {
            val notice = running(LedAnimationType.STATIC, sound)
            assertEquals(detail, notice.detail)
            assertEquals(R.string.runelight_solid, notice.title)
            assertTrue(notice.ongoing)
        }
    }

    @Test fun breathingAndPulseNeverAdvertiseActiveSoundEvenDuringAHandoff() {
        for (effect in listOf(LedAnimationType.BREATH, LedAnimationType.PULSE)) {
            for (sound in SoundPhase.entries.filter { it != SoundPhase.OFF })
                assertEquals(R.string.runelight_notice_sound_paused, running(effect, sound).detail)
            assertEquals(R.string.runelight_notification_body, running(effect).detail)
        }
    }

    @Test fun acceptedHandoffKeepsTheOldEffectUntilTheReplacementIsObserved() {
        val runtime = LightingRuntime()
        runtime.frameAccepted(runtime.begin(LedAnimationType.STATIC))
        val token = runtime.begin(LedAnimationType.RAINBOW, keepOutput = true)
        assertEquals(R.string.runelight_solid, LightingNotice.from(runtime.state, SoundState())!!.title)
        runtime.frameAccepted(token)
        assertEquals(R.string.runelight_rainbow, LightingNotice.from(runtime.state, SoundState())!!.title)
    }

    @Test fun captureAndOutputFailuresBecomeDismissibleStoppedNotices() {
        for ((failure, detail) in mapOf(LightingFailure.CAPTURE_LOST to R.string.runelight_notice_capture_lost,
            LightingFailure.OUTPUT_UNAVAILABLE to R.string.runelight_output_unavailable,
            LightingFailure.START_FAILED to R.string.runelight_start_failed)) {
            val notice = LightingNotice.from(LightingState(LightingPhase.FAILED, LedAnimationType.AMBIENT, failure),
                SoundState(SoundPhase.ACTIVE, true))!!
            assertFalse(notice.ongoing)
            assertEquals(R.string.runelight_notice_stopped, notice.title)
            assertEquals(detail, notice.detail)
        }
    }

    @Test fun identicalPresentationIgnoresCaptureLeaseChangesForDeduplication() {
        val state = LightingState(LightingPhase.RUNNING, LedAnimationType.AMBIENT)
        assertEquals(LightingNotice.from(state, SoundState(SoundPhase.ACTIVE, true)),
            LightingNotice.from(state, SoundState(SoundPhase.ACTIVE, false)))
        assertEquals(R.string.runelight_notice_sound_active, running(sound = SoundPhase.ACTIVE).detail)
        assertEquals(R.string.runelight_notice_sound_starting, running(sound = SoundPhase.STARTING).detail)
    }
}
