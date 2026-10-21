package com.moonbench.bifrost.services

import com.moonbench.bifrost.animations.LedAnimationType

enum class LightingPhase { STOPPED, STARTING, RUNNING, FAILED }
enum class LightingFailure { OUTPUT_UNAVAILABLE, START_FAILED }

/** Runtime observations only. Never persisted or interpreted as permission to start. */
data class LightingState(
    val phase: LightingPhase = LightingPhase.STOPPED,
    val effect: LedAnimationType? = null,
    val failure: LightingFailure? = null
)

/** Main-looper owner; tokens fence callbacks across both replacement and Stop. */
internal class LightingRuntime {
    var state = LightingState()
        private set
    private var generation = 0L

    fun begin(effect: LedAnimationType): Long {
        generation++
        state = LightingState(LightingPhase.STARTING, effect)
        return generation
    }

    fun frameAccepted(token: Long): Boolean {
        if (token != generation || state.phase != LightingPhase.STARTING) return false
        state = state.copy(phase = LightingPhase.RUNNING)
        return true
    }

    fun isCurrent(token: Long): Boolean = token == generation &&
        state.phase in setOf(LightingPhase.STARTING, LightingPhase.RUNNING)

    fun end(failure: LightingFailure? = null) {
        generation++
        state = if (failure == null) LightingState() else
            LightingState(LightingPhase.FAILED, state.effect, failure)
    }
}
