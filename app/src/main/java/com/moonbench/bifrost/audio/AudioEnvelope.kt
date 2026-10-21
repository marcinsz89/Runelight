package com.moonbench.bifrost.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.PI

/** Playback intensity only. Beat emphasizes transient energy; it does not estimate tempo. */
internal class AudioEnvelope {
    private var mode = SoundMode.LEVEL
    private var target = 0.0
    private var current = 0.0
    private var previous: Long? = null
    private var low250 = 0.0
    private var low40 = 0.0
    private var background: Double? = null
    private val alpha250 = 1 - exp(-2 * PI * 250 / 16000)
    private val alpha40 = 1 - exp(-2 * PI * 40 / 16000)

    fun setMode(next: SoundMode) {
        if (mode == next) return
        mode = next
        target = 0.0
        current = 0.0
        previous = null
        low250 = 0.0
        low40 = 0.0
        background = null
    }

    fun samples(buffer: ShortArray, count: Int) {
        require(count in 1..buffer.size)
        var squares = 0.0
        var bassSquares = 0.0
        for (index in 0 until count) {
            val sample = buffer[index].toDouble() / 32768.0
            squares += sample * sample
            if (mode == SoundMode.BEAT) {
                // Difference of low-pass filters rejects DC and emphasizes bass.
                low250 += alpha250 * (sample - low250)
                low40 += alpha40 * (sample - low40)
                val bass = low250 - low40
                bassSquares += bass * bass
            }
        }
        val rms = sqrt(squares / count)
        target = when (mode) {
            SoundMode.OFF -> 0.0
            SoundMode.LEVEL -> if (rms < 0.003) 0.0 else ((rms - 0.003) * 6.0).coerceIn(0.0, 1.0)
            SoundMode.BEAT -> {
                val energy = max(rms * 0.35, sqrt(bassSquares / count) * 1.7)
                val bed = background ?: energy
                // Use the previous background so a hit doesn't raise its own gate.
                val threshold = max(0.015, bed * 1.35)
                background = bed + (energy - bed) * (1 - exp(-count / 16000.0 / 0.7))
                ((energy - threshold) / max(0.07, bed * 1.25)).coerceIn(0.0, 1.0).pow(1.4)
            }
        }
    }

    fun frame(nowMs: Long): Float {
        val elapsed = previous?.let { (nowMs - it).coerceIn(0, 1000) } ?: 25L
        previous = nowMs
        val tau = if (mode == SoundMode.BEAT) {
            if (target > current) 10.0 else 70.0
        } else if (target > current) 35.0 else 180.0
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
