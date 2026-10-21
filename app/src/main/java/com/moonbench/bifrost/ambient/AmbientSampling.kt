package com.moonbench.bifrost.ambient

import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

internal enum class AmbientSamplingMode {
    EDGES, HALVES, FULL_SCREEN;

    fun next() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromStoredName(value: String?): AmbientSamplingMode =
            entries.firstOrNull { it.name == value } ?: EDGES
    }
}

internal data class AmbientColors(val left: Int, val right: Int)

/** Source regions and stride-safe RGB decoding; no Android objects or user-level scaling. */
internal object AmbientSampling {
    fun rgba(buffer: ByteBuffer, rowStride: Int, pixelStride: Int,
             left: Int, top: Int, width: Int, height: Int): IntArray {
        require(left >= 0 && top >= 0 && width > 1 && height > 0 && pixelStride >= 3)
        require(rowStride.toLong() >= (left.toLong() + width) * pixelStride)
        val last = (top.toLong() + height - 1) * rowStride + (left.toLong() + width - 1) * pixelStride + 2
        require(last < buffer.limit()) { "Invalid image strides/crop" }
        return IntArray(width * height) { index ->
            val offset = (top + index / width) * rowStride + (left + index % width) * pixelStride
            rgb(buffer.get(offset).toInt() and 255, buffer.get(offset + 1).toInt() and 255,
                buffer.get(offset + 2).toInt() and 255)
        }
    }

    fun sample(pixels: IntArray, width: Int, height: Int, mode: AmbientSamplingMode): AmbientColors {
        require(width > 1 && height > 0 && pixels.size == width * height)
        // Ignore one resampled perimeter cell: a narrow system gesture handle
        // can otherwise be the sole nonblack sample and dominate a dark picture.
        val guardX = if (width >= 8) 1 else 0
        val guardY = if (height >= 8) 1 else 0
        fun rowBlack(y: Int) = (guardX until width - guardX).all { pixels[y * width + it] and 0xffffff == 0 }
        var top = guardY
        var bottom = height - guardY
        // Trim only matching, completely black bars, at most one quarter per
        // edge. Interior black and all-black images retain exact zero output.
        while (top < height / 4 && rowBlack(top) && rowBlack(bottom - 1)) { top++; bottom-- }
        fun columnBlack(x: Int) = (top until bottom).all { pixels[it * width + x] and 0xffffff == 0 }
        var left = guardX
        var right = width - guardX
        while (left < width / 4 && columnBlack(left) && columnBlack(right - 1)) { left++; right-- }
        val middle = (left + right) / 2
        val band = ((right - left) / 8.0).roundToInt().coerceAtLeast(1)
        fun representative(start: Int, end: Int): Int {
            val region = IntArray((end - start) * (bottom - top))
            var index = 0
            for (y in top until bottom) for (x in start until end) region[index++] = pixels[y * width + x]
            return AmbientColorProfile.representative(region)
        }

        return when (mode) {
            AmbientSamplingMode.EDGES -> AmbientColors(representative(left, left + band), representative(right - band, right))
            AmbientSamplingMode.HALVES -> AmbientColors(representative(left, middle), representative(middle, right))
            AmbientSamplingMode.FULL_SCREEN -> representative(left, right).let { AmbientColors(it, it) }
        }
    }

    fun rgb(red: Int, green: Int, blue: Int) = (255 shl 24) or (red shl 16) or (green shl 8) or blue
}

/** Held targets advance on a clock, even if capture produces no changed frames. */
internal class AmbientSmoother {
    private val current = DoubleArray(6)
    private val target = IntArray(6)
    private var previousTime: Long? = null
    var settled = true
        private set

    fun target(colors: AmbientColors, nowMs: Long) {
        if (settled) previousTime = nowMs
        for (side in 0..1) for (channel in 0..2) {
            val index = side * 3 + channel
            target[index] = ((if (side == 0) colors.left else colors.right) ushr (16 - channel * 8)) and 255
        }
        settled = current.indices.all { current[it] == target[it].toDouble() }
    }

    fun frame(nowMs: Long): AmbientColors {
        val elapsed = previousTime?.let { (nowMs - it).coerceIn(0, 1000) } ?: 33L
        previousTime = nowMs
        val factor = 1.0 - exp(-elapsed / 35.0)
        for (index in current.indices) {
            val value = current[index] + (target[index] - current[index]) * factor
            current[index] = if (abs(target[index] - value) <= 0.5) target[index].toDouble() else value
        }
        settled = current.indices.all { current[it] == target[it].toDouble() }
        fun color(start: Int) = AmbientSampling.rgb(current[start].roundToInt(), current[start + 1].roundToInt(), current[start + 2].roundToInt())
        return AmbientColors(color(0), color(3))
    }
}
