package com.moonbench.bifrost.audio

import com.moonbench.bifrost.animations.LedAnimationType

internal object SoundPolicy {
    fun supports(effect: LedAnimationType?) = when (effect) {
        LedAnimationType.AMBIENT, LedAnimationType.STATIC, LedAnimationType.RAINBOW -> true
        else -> false
    }
}

enum class SoundPhase { OFF, PAUSED, NEEDS_CONSENT, NEEDS_PERMISSION, STARTING, ACTIVE, FAILED }
data class SoundState(val phase: SoundPhase = SoundPhase.OFF, val captureAvailable: Boolean = false)
