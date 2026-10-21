package com.moonbench.bifrost.services

import com.moonbench.bifrost.R
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.audio.SoundPhase
import com.moonbench.bifrost.audio.SoundPolicy
import com.moonbench.bifrost.audio.SoundState
import com.moonbench.bifrost.audio.SoundMode

/** Presentation only: a notice can never start lighting or authorize capture. */
internal data class LightingNotice(val title: Int, val detail: Int, val ongoing: Boolean) {
    companion object {
        fun from(state: LightingState, sound: SoundState): LightingNotice? = when (state.phase) {
            LightingPhase.STOPPED -> null
            LightingPhase.FAILED -> LightingNotice(R.string.runelight_notice_stopped, when (state.failure) {
                LightingFailure.CAPTURE_LOST -> R.string.runelight_notice_capture_lost
                LightingFailure.OUTPUT_UNAVAILABLE -> R.string.runelight_output_unavailable
                else -> R.string.runelight_start_failed
            }, false)
            LightingPhase.STARTING -> LightingNotice(R.string.runelight_starting, effectLabel(state.effect), true)
            LightingPhase.RUNNING -> LightingNotice(effectLabel(state.effect), when {
                sound.phase == SoundPhase.OFF -> R.string.runelight_notification_body
                !SoundPolicy.supports(state.effect) -> R.string.runelight_notice_sound_paused
                else -> when (sound.phase) {
                    SoundPhase.ACTIVE -> if (sound.mode == SoundMode.BEAT) R.string.runelight_notice_beat_active
                        else R.string.runelight_notice_sound_active
                    SoundPhase.STARTING -> R.string.runelight_notice_sound_starting
                    SoundPhase.NEEDS_PERMISSION -> R.string.runelight_notice_sound_permission
                    SoundPhase.NEEDS_CONSENT -> R.string.runelight_notice_sound_consent
                    SoundPhase.FAILED -> R.string.runelight_notice_sound_failed
                    else -> R.string.runelight_notice_sound_paused
                }
            }, true)
        }

        fun effectLabel(effect: LedAnimationType?): Int = when (effect) {
            LedAnimationType.AMBIENT -> R.string.runelight_ambient
            LedAnimationType.STATIC -> R.string.runelight_solid
            LedAnimationType.RAINBOW -> R.string.runelight_rainbow
            LedAnimationType.BREATH -> R.string.runelight_breathing
            LedAnimationType.PULSE -> R.string.runelight_pulse
            else -> R.string.runelight_lighting
        }
    }
}
