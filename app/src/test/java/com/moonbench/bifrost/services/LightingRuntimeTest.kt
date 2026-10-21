package com.moonbench.bifrost.services

import com.moonbench.bifrost.animations.LedAnimationType
import org.junit.Assert.*
import org.junit.Test

class LightingRuntimeTest {
    @Test fun aHandoffKeepsAcknowledgedOutputOnUntilTheNewFrameArrives() {
        val runtime = LightingRuntime()
        val old = runtime.begin(LedAnimationType.STATIC)
        runtime.frameAccepted(old)
        val next = runtime.begin(LedAnimationType.RAINBOW, keepOutput = true)
        assertEquals(LightingState(LightingPhase.RUNNING, LedAnimationType.STATIC), runtime.state)
        assertTrue(runtime.awaitingFrame(next))
        assertFalse(runtime.frameAccepted(old))
        assertTrue(runtime.frameAccepted(next))
        assertEquals(LightingState(LightingPhase.RUNNING, LedAnimationType.RAINBOW), runtime.state)
        assertFalse(runtime.awaitingFrame(next))
    }

    @Test fun heldOutputStillRequiresAReplacementFrameAndCanFailOrStop() {
        val runtime = LightingRuntime()
        runtime.frameAccepted(runtime.begin(LedAnimationType.STATIC))
        val next = runtime.begin(LedAnimationType.PULSE, keepOutput = true)
        assertTrue(runtime.awaitingFrame(next))
        runtime.end(LightingFailure.START_FAILED)
        assertFalse(runtime.awaitingFrame(next))
        assertEquals(LightingState(LightingPhase.FAILED, LedAnimationType.PULSE, LightingFailure.START_FAILED), runtime.state)
        assertFalse(runtime.frameAccepted(next))
        runtime.end()
        assertEquals(LightingState(), runtime.state)
    }

    @Test fun anUnacknowledgedStartCannotClaimHeldOutput() {
        val runtime = LightingRuntime()
        runtime.begin(LedAnimationType.STATIC, keepOutput = true)
        assertEquals(LightingPhase.STARTING, runtime.state.phase)
    }

    @Test fun aStartNeedsAnAcceptedFrameBeforeItIsRunning() {
        val runtime = LightingRuntime()
        val token = runtime.begin(LedAnimationType.STATIC)
        assertEquals(LightingPhase.STARTING, runtime.state.phase)
        assertTrue(runtime.frameAccepted(token))
        assertEquals(LightingState(LightingPhase.RUNNING, LedAnimationType.STATIC), runtime.state)
        assertFalse(runtime.frameAccepted(token))
    }

    @Test fun aReplacementRejectsBothSuccessAndFailureFromItsPredecessor() {
        val runtime = LightingRuntime()
        val old = runtime.begin(LedAnimationType.RAINBOW)
        val current = runtime.begin(LedAnimationType.BREATH)
        assertFalse(runtime.frameAccepted(old))
        assertFalse(runtime.isCurrent(old))
        assertEquals(LightingPhase.STARTING, runtime.state.phase)
        assertTrue(runtime.frameAccepted(current))
        assertEquals(LedAnimationType.BREATH, runtime.state.effect)
    }

    @Test fun stopInvalidatesEvenAnAlreadyRunningSession() {
        val runtime = LightingRuntime()
        val token = runtime.begin(LedAnimationType.PULSE)
        runtime.frameAccepted(token)
        runtime.end()
        assertFalse(runtime.isCurrent(token))
        assertFalse(runtime.frameAccepted(token))
        assertEquals(LightingState(), runtime.state)
    }

    @Test fun aFailureStaysVisibleUntilAnExplicitNewStartOrStop() {
        val runtime = LightingRuntime()
        val old = runtime.begin(LedAnimationType.STATIC)
        runtime.end(LightingFailure.OUTPUT_UNAVAILABLE)
        assertEquals(LightingPhase.FAILED, runtime.state.phase)
        assertFalse(runtime.isCurrent(old))
        val retry = runtime.begin(LedAnimationType.RAINBOW)
        assertNull(runtime.state.failure)
        assertTrue(runtime.frameAccepted(retry))
        runtime.end()
        assertNull(runtime.state.failure)
    }
}
