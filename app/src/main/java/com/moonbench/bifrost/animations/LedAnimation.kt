package com.moonbench.bifrost.animations

import android.graphics.Color
import com.moonbench.bifrost.tools.DeviceInfo
import com.moonbench.bifrost.tools.LedController
import kotlin.math.abs
import kotlin.math.roundToInt

abstract class LedAnimation(protected val ledController: LedController) {

    companion object {
        private const val THOR_LOW_BRIGHTNESS_THRESHOLD = 51
        private const val THOR_LOW_BRIGHTNESS_SPEED_MULTIPLIER = 1.35f
    }

    abstract val type: LedAnimationType
    abstract val needsColorSelection: Boolean

    abstract fun start()
    abstract fun stop()

    /** End this effect's callbacks while the service hands output to another effect. */
    open fun stopForHandoff() = stop()

    open fun setTargetColor(color: Int) {}
    open fun setTargetRightColor(color: Int) {}
    open fun setTargetBrightness(brightness: Int) {}
    open fun setLerpStrength(strength: Float) {}
    open fun setSpeed(speed: Float) {}

    protected fun lerpInt(from: Int, to: Int, factor: Float): Int {
        if (from == to) return from.coerceIn(0, 255)
        val f = factor.coerceIn(0f, 1f)
        val raw = from + (to - from) * f
        val result = if (abs(to - raw) < 1f) to else raw.roundToInt()
        return result.coerceIn(0, 255)
    }

    protected fun lerpBrightnessInt(from: Int, to: Int, factor: Float): Int {
        val isThorLowBrightnessRange =
            DeviceInfo.isAynThor &&
                (from <= THOR_LOW_BRIGHTNESS_THRESHOLD || to <= THOR_LOW_BRIGHTNESS_THRESHOLD)

        val adjustedFactor = if (isThorLowBrightnessRange) {
            (factor * THOR_LOW_BRIGHTNESS_SPEED_MULTIPLIER).coerceAtMost(0.92f)
        } else {
            factor
        }

        val interpolated = lerpInt(from, to, adjustedFactor)
        if (!isThorLowBrightnessRange || from == to) return interpolated

        if (interpolated == from) {
            val distance = abs(to - from)
            val minStep = when {
                distance >= 18 -> 3
                distance >= 8 -> 2
                else -> 1
            }
            return if (to > from) {
                (from + minStep).coerceAtMost(to).coerceIn(0, 255)
            } else {
                (from - minStep).coerceAtLeast(to).coerceIn(0, 255)
            }
        }

        return interpolated
    }

    protected fun adjustedAnimationDelay(baseDelayMs: Long, brightness: Int): Long {
        return baseDelayMs
    }

    protected fun lerpColor(from: Int, to: Int, factor: Float): Int {
        val r = lerpInt(Color.red(from), Color.red(to), factor)
        val g = lerpInt(Color.green(from), Color.green(to), factor)
        val b = lerpInt(Color.blue(from), Color.blue(to), factor)
        return Color.rgb(r, g, b)
    }

}
