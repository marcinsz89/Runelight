package com.moonbench.bifrost.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.SystemClock

internal interface PlaybackRecorder {
    fun start()
    fun read(buffer: ShortArray): Int
    fun close()
}

/** Main-looper owner. Nonblocking reads avoid a read-thread/join shutdown race. */
internal class PlaybackAudioCapture(
    context: Context,
    projection: MediaProjection?,
    private val handler: Handler,
    private val onLevel: (Float) -> Unit,
    private val onState: (SoundPhase) -> Unit,
    private val recorderFactory: () -> PlaybackRecorder = {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw SecurityException("Playback capture requires audio permission")
        val config = AudioPlaybackCaptureConfiguration.Builder(checkNotNull(projection))
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
        val record = try {
            AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(16000)
                .setAudioPlaybackCaptureConfig(config).build()
        } catch (denied: SecurityException) {
            // Permission can be revoked between the check and native allocation.
            throw IllegalStateException("Playback capture permission was lost", denied)
        }
        object : PlaybackRecorder {
            override fun start() {
                check(record.state == AudioRecord.STATE_INITIALIZED)
                record.startRecording()
                check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            }
            override fun read(buffer: ShortArray) = record.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
            override fun close() { try { runCatching { record.stop() } } finally { record.release() } }
        }
    }
) {
    private var recorder: PlaybackRecorder? = null
    private var active = false
    private var announced = false
    private val buffer = ShortArray(4096)
    private val envelope = AudioEnvelope()
    private var health = AudioReadHealth(0)
    private var lastLevel = -1f
    private val poll = object : Runnable {
        override fun run() {
            if (!active) return
            try {
                val count = recorder?.read(buffer) ?: return
                val now = SystemClock.elapsedRealtime()
                check(health.healthy(count, now))
                if (count > 0) {
                    envelope.samples(buffer, count)
                    if (!announced) { announced = true; onState(SoundPhase.ACTIVE) }
                }
                if (!active) return
                val level = envelope.frame(now)
                if (level != lastLevel) { lastLevel = level; onLevel(level) }
                if (active) handler.postDelayed(this, 25)
            } catch (_: RuntimeException) { fail() }
        }
    }

    fun start() {
        check(!active)
        active = true
        health = AudioReadHealth(SystemClock.elapsedRealtime())
        try {
            recorder = recorderFactory()
            recorder!!.start()
            onState(SoundPhase.STARTING)
            handler.post(poll)
        } catch (_: RuntimeException) { fail() }
    }

    private fun fail() {
        if (!active) return
        close()
        onState(SoundPhase.FAILED)
    }

    fun close() {
        active = false
        handler.removeCallbacks(poll)
        val old = recorder
        recorder = null
        runCatching { old?.close() }
    }
}
