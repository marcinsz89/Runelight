package com.moonbench.bifrost.animations

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.moonbench.bifrost.tools.LedController
import kotlin.math.roundToInt

class StaticAnimation(
    ledController: LedController,
    initialColor: Int,
    initialRightColor: Int = initialColor
) : LedAnimation(ledController) {

    override val type: LedAnimationType = LedAnimationType.STATIC
    override val needsColorSelection: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private var targetColor: Int = initialColor or Color.BLACK
    private var currentColor: Int = targetColor

    private var targetRightColor: Int = initialRightColor or Color.BLACK
    private var currentRightColor: Int = targetRightColor

    private var targetBrightness: Int = 255
    private var currentBrightness: Int = 255

    private var lerpStrength: Float = 0.5f

    /** Main-looper observation used by the idle-work regression. */
    internal val hasPendingFrame: Boolean get() = handler.hasCallbacks(runnable)

    private fun scheduleFrame() {
        if (running && !handler.hasCallbacks(runnable)) handler.post(runnable)
    }

    override fun setTargetColor(color: Int) {
        val opaque = color or Color.BLACK
        if (targetColor == opaque) return
        targetColor = opaque
        scheduleFrame()
    }

    override fun setTargetRightColor(color: Int) {
        val opaque = color or Color.BLACK
        if (targetRightColor == opaque) return
        targetRightColor = opaque
        scheduleFrame()
    }

    override fun setTargetBrightness(brightness: Int) {
        val bounded = brightness.coerceIn(0, 255)
        if (targetBrightness == bounded) return
        targetBrightness = bounded
        scheduleFrame()
    }

    override fun setLerpStrength(strength: Float) {
        lerpStrength = strength.coerceIn(0f, 1f)
    }

    // Integer rounding must not leave a channel permanently one or two steps
    // short of the target, including when fading to genuine black.
    private fun step(from: Int, to: Int, factor: Float): Int {
        val next = lerpInt(from, to, factor)
        return if (next == from && from != to) from + (if (to > from) 1 else -1) else next
    }

    private fun stepColor(from: Int, to: Int, factor: Float) = Color.rgb(
        step(Color.red(from), Color.red(to), factor),
        step(Color.green(from), Color.green(to), factor),
        step(Color.blue(from), Color.blue(to), factor)
    )

    private val runnable = object : Runnable {
        override fun run() {
            if (!running) return

            val factor = 0.15f + 0.7f * lerpStrength
            currentColor = stepColor(currentColor, targetColor, factor)
            currentRightColor = stepColor(currentRightColor, targetRightColor, factor)
            val nextBrightness = lerpBrightnessInt(currentBrightness, targetBrightness, factor)
            currentBrightness = if (nextBrightness == currentBrightness && currentBrightness != targetBrightness)
                step(currentBrightness, targetBrightness, factor) else nextBrightness

            val scale = currentBrightness / 255f

            val lr = (Color.red(currentColor) * scale).roundToInt().coerceIn(0, 255)
            val lg = (Color.green(currentColor) * scale).roundToInt().coerceIn(0, 255)
            val lb = (Color.blue(currentColor) * scale).roundToInt().coerceIn(0, 255)

            val rr = (Color.red(currentRightColor) * scale).roundToInt().coerceIn(0, 255)
            val rg = (Color.green(currentRightColor) * scale).roundToInt().coerceIn(0, 255)
            val rb = (Color.blue(currentRightColor) * scale).roundToInt().coerceIn(0, 255)

            ledController.setLedColorDual(lr, lg, lb, rr, rg, rb)

            if (currentColor != targetColor || currentRightColor != targetRightColor || currentBrightness != targetBrightness) {
                handler.postDelayed(this, adjustedAnimationDelay(30L, targetBrightness))
            }
        }
    }

    override fun start() {
        if (running) return
        running = true
        handler.post(runnable)
    }

    override fun stop() = stop(clearOutput = true)

    override fun stopForHandoff() = stop(clearOutput = false)

    private fun stop(clearOutput: Boolean) {
        running = false
        handler.removeCallbacksAndMessages(null)
        if (!clearOutput) return
        ledController.setLedColor(
            0,
            0,
            0,
            leftTop = true,
            leftBottom = true,
            rightTop = true,
            rightBottom = true
        )
    }
}
