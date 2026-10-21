package com.moonbench.bifrost.tools

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock

internal fun interface LedOutputDispatcher {
    fun submit(write: () -> Boolean, completed: (Boolean) -> Unit)
}

/** Shared across controller lifetimes so Stop/new Start cannot race two writers. */
internal object VendorLedOutput : LedOutputDispatcher {
    private val thread = HandlerThread("RunelightLedOutput").apply { start() }
    private val handler = Handler(thread.looper)
    private val writer = LatestValueWriter<() -> Boolean>(SystemClock::elapsedRealtime,
        { delay, task -> handler.postDelayed(task, delay) }, { it() })

    override fun submit(write: () -> Boolean, completed: (Boolean) -> Unit) = writer.offer(write, completed)
}

/** Local Binder seam for animation math tests; queue behavior has separate regressions. */
internal object ImmediateLedOutput : LedOutputDispatcher {
    override fun submit(write: () -> Boolean, completed: (Boolean) -> Unit) { completed(write()) }
}
