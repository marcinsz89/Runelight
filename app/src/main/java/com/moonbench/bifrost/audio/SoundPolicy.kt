package com.moonbench.bifrost.audio

import com.moonbench.bifrost.animations.LedAnimationType

internal object SoundPolicy {
    fun supports(effect: LedAnimationType?) = when (effect) {
        LedAnimationType.AMBIENT, LedAnimationType.STATIC, LedAnimationType.RAINBOW -> true
        else -> false
    }
}

enum class SoundPhase { OFF, PAUSED, NEEDS_CONSENT, NEEDS_PERMISSION, STARTING, ACTIVE, FAILED }

enum class SoundMode {
    OFF, LEVEL, BEAT;

    val enabled: Boolean get() = this != OFF
    fun next() = entries[(ordinal + 1) % entries.size]

    companion object {
        /** An explicit mode wins; migrate the original saved toggle without losing On. */
        fun fromStoredName(name: String?, legacyEnabled: Boolean = false): SoundMode =
            entries.firstOrNull { it.name == name } ?: if (legacyEnabled) LEVEL else OFF
    }
}

data class SoundState(val phase: SoundPhase = SoundPhase.OFF, val captureAvailable: Boolean = false,
    val mode: SoundMode = SoundMode.OFF)
