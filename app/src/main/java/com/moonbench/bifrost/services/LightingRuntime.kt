package com.moonbench.bifrost.services

import com.moonbench.bifrost.animations.LedAnimationType

enum class LightingPhase { STOPPED, STARTING, RUNNING, FAILED }
enum class LightingFailure { OUTPUT_UNAVAILABLE, START_FAILED, CAPTURE_LOST }

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
    private var pendingEffect: LedAnimationType? = null

    fun begin(effect: LedAnimationType, keepOutput: Boolean = false): Long {
        generation++
        pendingEffect = effect
        // During a plain handoff the acknowledged old frame remains lit. Keep
        // reporting it until the replacement supplies an accepted frame.
        if (!keepOutput || state.phase != LightingPhase.RUNNING)
            state = LightingState(LightingPhase.STARTING, effect)
        return generation
    }

    fun frameAccepted(token: Long): Boolean {
        if (!awaitingFrame(token)) return false
        state = LightingState(LightingPhase.RUNNING, pendingEffect)
        pendingEffect = null
        return true
    }

    fun awaitingFrame(token: Long): Boolean = isCurrent(token) && pendingEffect != null

    fun isCurrent(token: Long): Boolean = token == generation &&
        state.phase in setOf(LightingPhase.STARTING, LightingPhase.RUNNING)

    fun end(failure: LightingFailure? = null) {
        generation++
        state = if (failure == null) LightingState() else
            LightingState(LightingPhase.FAILED, pendingEffect ?: state.effect, failure)
        pendingEffect = null
    }
}
