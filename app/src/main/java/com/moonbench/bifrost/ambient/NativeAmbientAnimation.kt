package com.moonbench.bifrost.ambient

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.moonbench.bifrost.animations.LedAnimation
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.tools.LedController

/** Native Ambient path; legacy analyzer/animation remain disconnected. */
internal class NativeAmbientAnimation(
    context: Context,
    controller: LedController,
    private val resultCode: Int,
    private val grant: Intent,
    private var samplingMode: AmbientSamplingMode,
    onFailure: () -> Unit,
    captureFactory: ((IntArray, Int, Int) -> Unit, () -> Unit) -> AmbientCapture = { frame, failure ->
        TopScreenCapture(context, Handler(Looper.getMainLooper()), frame, failure)
    }
) : LedAnimation(controller) {
    override val type = LedAnimationType.AMBIENT
    override val needsColorSelection = false
    private val handler = Handler(Looper.getMainLooper())
    internal var hasRenderedFrame = false
        private set
    private var active = false
    private var scheduled = false
    private val smoother = AmbientSmoother()
    private var pixels: IntArray? = null
    private var width = 0
    private var height = 0
    private val capture = captureFactory({ frame, w, h ->
        if (active) {
            pixels = frame; width = w; height = h
            updateTarget()
        }
    }, { if (active) onFailure() })
    private val render = object : Runnable {
        override fun run() {
            scheduled = false
            if (!active) return
            val frame = smoother.frame(SystemClock.elapsedRealtime())
            fun channel(color: Int, shift: Int) = (color ushr shift) and 255
            hasRenderedFrame = true
            ledController.setLedColorDual(channel(frame.left, 16), channel(frame.left, 8), channel(frame.left, 0),
                channel(frame.right, 16), channel(frame.right, 8), channel(frame.right, 0))
            if (active && !smoother.settled) schedule()
        }
    }

    override fun start() { active = true; capture.start(resultCode, grant) }
    override fun stop() {
        active = false
        handler.removeCallbacks(render)
        scheduled = false
        capture.close()
        pixels = null
    }

    fun setSamplingMode(value: AmbientSamplingMode) {
        if (samplingMode == value) return
        samplingMode = value
        updateTarget() // Reuse held pixels immediately, without a new consent/frame.
    }

    private fun updateTarget() {
        val frame = pixels ?: return
        smoother.target(AmbientSampling.sample(frame, width, height, samplingMode), SystemClock.elapsedRealtime())
        if (!smoother.settled || !hasRenderedFrame) schedule()
    }

    private fun schedule() {
        if (!active || scheduled) return
        scheduled = true
        handler.postDelayed(render, 16)
    }
}
