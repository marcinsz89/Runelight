package com.moonbench.bifrost.ambient

import org.junit.Assert.*
import org.junit.Test

class ProjectionConsentGateTest {
    @Test fun explicitStopRejectsLateConsentAndAlreadyQueuedStart() {
        val gate = ProjectionConsentGate()
        val ticket = gate.begin()
        assertTrue(gate.isPending(ticket))
        gate.invalidate()
        assertFalse(gate.isPending(ticket))
        assertFalse(gate.consume(ticket))
    }
    @Test fun grantIsSingleUseAndOldCancellationCannotCancelReplacement() {
        val gate = ProjectionConsentGate()
        val old = gate.begin()
        val replacement = gate.begin()
        gate.cancel(old)
        assertFalse(gate.consume(old))
        assertTrue(gate.consume(replacement))
        assertFalse(gate.consume(replacement))
    }
}
