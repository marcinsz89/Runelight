package com.moonbench.bifrost.ambient

/** In-memory request identity only. Grants never enter preferences or this gate. */
internal class ProjectionConsentGate {
    private var epoch = 0L
    private var pending: Long? = null
    @Synchronized fun begin(): Long = (++epoch).also { pending = it }
    @Synchronized fun isPending(ticket: Long): Boolean = pending == ticket
    @Synchronized fun consume(ticket: Long): Boolean {
        if (pending != ticket) return false
        pending = null
        return true
    }
    @Synchronized fun cancel(ticket: Long) { if (pending == ticket) pending = null }
    @Synchronized fun invalidate() { pending = null; epoch++ }
}
