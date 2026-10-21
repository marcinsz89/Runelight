package com.moonbench.bifrost.tools

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class LedController internal constructor(private val pServerBinder: IBinder?) {
    constructor() : this(findVendorBinder())

    companion object {
        private const val TAG = "LedController"
        private fun findVendorBinder(): IBinder? = try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
            getService.invoke(serviceManager, "PServerBinder") as? IBinder
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get PServerBinder", e)
            null
        }
    }

    @Volatile private var closed = false
    /** Reports Binder submission, not physical LED acknowledgement. Never persisted. */
    @Volatile internal var onOutputResult: ((Boolean) -> Unit)? = null
    private var hasSubmittedFrame = false
    private val deathRecipient = IBinder.DeathRecipient { onOutputResult?.invoke(false) }
    init {
        runCatching { pServerBinder?.linkToDeath(deathRecipient, 0) }
    }
    val isAvailable: Boolean get() = !closed && pServerBinder?.isBinderAlive == true
    private val lock = ReentrantLock()

    private val frame = LedFrameState()

    fun setLedColor(
        red: Int,
        green: Int,
        blue: Int,
        brightness: Int = 255,
        leftTop: Boolean = true,
        leftBottom: Boolean = true,
        rightTop: Boolean = true,
        rightBottom: Boolean = true
    ) {
        lock.withLock {
            if (closed) return
            val color = LedFrameState.Color(red, green, blue, brightness)
            frame.update(color, color, booleanArrayOf(leftTop, leftBottom, rightTop, rightBottom))
            emitFrame()
        }
    }

    fun setLedColorDual(
        leftR: Int, leftG: Int, leftB: Int,
        rightR: Int, rightG: Int, rightB: Int,
        brightness: Int = 255,
        leftTop: Boolean = true,
        leftBottom: Boolean = true,
        rightTop: Boolean = true,
        rightBottom: Boolean = true
    ) {
        lock.withLock {
            if (closed) return
            frame.update(
                LedFrameState.Color(leftR, leftG, leftB, brightness),
                LedFrameState.Color(rightR, rightG, rightB, brightness),
                booleanArrayOf(leftTop, leftBottom, rightTop, rightBottom)
            )
            emitFrame()
        }
    }

    /** Independent user levels apply once, after effect color and before submission. */
    fun setSideBrightness(leftPercent: Int? = null, rightPercent: Int? = null) {
        lock.withLock {
            if (closed) return
            leftPercent?.let { frame.leftPercent = it.coerceIn(0, 100) }
            rightPercent?.let { frame.rightPercent = it.coerceIn(0, 100) }
            emitFrame()
        }
    }

    fun setMasterScale(scale: Float) {
        lock.withLock {
            if (closed) return
            frame.masterScale = scale.coerceIn(0f, 1f)
            emitFrame()
        }
    }

    fun resetFadeBaseline() {
        lock.withLock { if (!closed) frame.blackBaseline() }
    }

    // Legacy API, retaining its existing black-RGB / wire-field behavior.
    fun setBrightness(brightness: Int) = setLedColor(0, 0, 0, brightness)

    /** Caller holds the lock across raw state, final scaling and Binder submission. */
    private fun emitFrame() {
        val listener = onOutputResult
        val binder = pServerBinder
        if (binder == null || !binder.isBinderAlive) {
            listener?.invoke(false)
            return
        }
        val submission = frame.pending()
        if (submission == null) {
            if (hasSubmittedFrame) listener?.invoke(true)
            return
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeStringArray(arrayOf(submission.command, "1"))
            if (binder.transact(0, data, reply, IBinder.FLAG_ONEWAY)) {
                // Submission is not a hardware acknowledgement. Failed calls stay retryable.
                frame.markSubmitted(submission)
                hasSubmittedFrame = true
                listener?.invoke(true)
            } else {
                listener?.invoke(false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "LED transact failed", e)
            listener?.invoke(false)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun shutdown() {
        lock.withLock {
            if (closed) return
            onOutputResult = null
            try {
                val black = LedFrameState.Color(0, 0, 0)
                frame.update(black, black, booleanArrayOf(true, true, true, true))
                emitFrame()
            } finally {
                // Any late callback still holding this driver loses output ownership.
                // A replacement session must use a new controller instance.
                closed = true
                runCatching { pServerBinder?.unlinkToDeath(deathRecipient, 0) }
            }
        }
    }
}
