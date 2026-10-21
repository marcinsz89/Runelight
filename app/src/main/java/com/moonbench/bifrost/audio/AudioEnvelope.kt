package com.moonbench.bifrost.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** Held playback energy, never microphone input. Fixed first-slice tuning. */
internal class AudioEnvelope {
    private var target = 0.0
    private var current = 0.0
    private var previous: Long? = null

    fun samples(buffer: ShortArray, count: Int) {
        require(count in 1..buffer.size)
        var squares = 0.0
        for (index in 0 until count) squares += buffer[index].toDouble() * buffer[index]
        val rms = sqrt(squares / count) / 32768.0
        target = if (rms < 0.003) 0.0 else ((rms - 0.003) * 6.0).coerceIn(0.0, 1.0)
    }

    fun frame(nowMs: Long): Float {
        val elapsed = previous?.let { (nowMs - it).coerceIn(0, 1000) } ?: 25L
        previous = nowMs
        val tau = if (target > current) 35.0 else 180.0
        val value = current + (target - current) * (1 - exp(-elapsed / tau))
        current = if (abs(value - target) < 0.001) target else value
        return current.toFloat()
    }
}

/** Zero is normal for a nonblocking read; negative results fail immediately. */
internal class AudioReadHealth(startMs: Long) {
    private var lastDataAt = startMs
    fun healthy(count: Int, nowMs: Long): Boolean {
        if (count < 0) return false
        if (count > 0) lastDataAt = nowMs
        return nowMs - lastDataAt < 5000
    }
}
