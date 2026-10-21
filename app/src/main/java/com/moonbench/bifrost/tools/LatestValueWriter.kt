package com.moonbench.bifrost.tools

/** One blocking write plus one replaceable value; never a FIFO of stale frames. */
internal class LatestValueWriter<T>(
    private val nowMs: () -> Long,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val write: (T) -> Boolean,
    private val minimumIntervalMs: Long = 40
) {
    private data class Pending<T>(val value: T, val completed: (Boolean) -> Unit)
    private val lock = Any()
    private var pending: Pending<T>? = null
    private var scheduled = false
    private var writing = false
    private var lastStarted: Long? = null

    fun offer(value: T, completed: (Boolean) -> Unit) = synchronized(lock) {
        pending = Pending(value, completed)
        arm()
    }

    private fun arm() {
        if (scheduled || writing || pending == null) return
        scheduled = true
        val delay = lastStarted?.let { (it + minimumIntervalMs - nowMs()).coerceAtLeast(0) } ?: 0
        schedule(delay, ::drain)
    }

    private fun drain() {
        val next = synchronized(lock) {
            scheduled = false
            val next = pending ?: return
            pending = null
            writing = true
            lastStarted = nowMs()
            next
        }
        val accepted = runCatching { write(next.value) }.getOrDefault(false)
        try {
            next.completed(accepted)
        } finally {
            synchronized(lock) { writing = false; arm() }
        }
    }
}
