package com.moonbench.bifrost.ambient

import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Colorful-pixel selection follows Bifrost ScreenAnalyser's top-weighted sampling
 * idea (GPLv3, upstream attribution retained in the repository). This bounded
 * histogram includes tied scores equally, so scan order cannot favor one side.
 */
internal object AmbientColorProfile {
    private const val SCORE_BINS = 1024
    private const val SELECTED_FRACTION = 0.30
    private const val SATURATION_GAIN = 1.35
    private const val VALUE_POWER = 0.80

    fun representative(pixels: IntArray): Int {
        require(pixels.isNotEmpty())
        val counts = IntArray(SCORE_BINS)
        val scores = IntArray(pixels.size)
        for (index in pixels.indices) {
            val color = pixels[index]
            val high = maxOf(channel(color, 16), channel(color, 8), channel(color, 0)) / 255.0
            val low = minOf(channel(color, 16), channel(color, 8), channel(color, 0)) / 255.0
            val saturation = if (high == 0.0) 0.0 else (high - low) / high
            // Saturation and chroma carry identity; value gives every hue the
            // same brightness weight. White HUDs cannot automatically win.
            val weight = (0.15 * high + 0.55 * saturation.pow(0.3) + 0.30 * (high - low)) * sqrt(high)
            val score = if (high == 0.0) 0 else
                (weight * (SCORE_BINS - 1)).roundToInt().coerceIn(1, SCORE_BINS - 1)
            scores[index] = score
            counts[score]++
        }
        val wanted = ceil(pixels.size * SELECTED_FRACTION).toInt().coerceAtLeast(1)
        var selected = 0
        var cutoff = SCORE_BINS - 1
        while (cutoff > 0) {
            selected += counts[cutoff]
            if (selected >= wanted) break
            cutoff--
        }
        val sums = DoubleArray(3)
        var weights = 0.0
        for (index in pixels.indices) {
            val score = scores[index]
            if (score < cutoff) continue
            val weight = score.toDouble()
            for (component in 0..2) sums[component] += channel(pixels[index], 16 - component * 8) * weight
            weights += weight
        }
        if (weights == 0.0) return AmbientSampling.rgb(0, 0, 0)
        return enhance(sums[0] / weights, sums[1] / weights, sums[2] / weights)
    }

    private fun enhance(red: Double, green: Double, blue: Double): Int {
        val high = maxOf(red, green, blue)
        val low = minOf(red, green, blue)
        if (high == 0.0) return AmbientSampling.rgb(0, 0, 0)
        val saturation = (high - low) / high
        val boostedSaturation = (saturation * SATURATION_GAIN).coerceAtMost(1.0)
        val value = (high / 255.0).pow(VALUE_POWER) * 255.0
        // HSV S/V adjustment expressed as a channel transform: hue is retained
        // without angle conversion (including red-magenta wrap and grayscale).
        fun output(component: Double): Int {
            val normalized = if (high == low) 1.0 else
                1.0 - ((high - component) / (high - low)) * boostedSaturation
            return (normalized * value).roundToInt().coerceIn(0, 255)
        }
        return AmbientSampling.rgb(output(red), output(green), output(blue))
    }

    private fun channel(color: Int, shift: Int) = color ushr shift and 255
}
