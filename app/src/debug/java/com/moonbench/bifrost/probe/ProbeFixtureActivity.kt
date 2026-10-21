package com.moonbench.bifrost.probe

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import kotlin.math.PI
import kotlin.math.sin

/** Known full-surface split pattern. Multiple tasks can occupy separate displays. */
class ProbeFixtureActivity : Activity() {
    private var leftColor = Color.RED
    private var rightColor = Color.BLUE
    private var label = "FIXTURE"
    private var showLabel = true
    private var darkHudScene = false
    private var track: AudioTrack? = null
    private val main = Handler(Looper.getMainLooper())
    private lateinit var pattern: View
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setDecorFitsSystemWindows(false)
        pattern = object : View(this) {
            val paint = Paint()
            override fun onDraw(canvas: Canvas) {
                paint.color = leftColor; canvas.drawRect(0f, 0f, width / 2f, height.toFloat(), paint)
                paint.color = rightColor; canvas.drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), paint)
                if (darkHudScene) {
                    paint.color = Color.WHITE
                    canvas.drawRect(0f, 0f, width.toFloat(), height * 0.12f, paint)
                    paint.color = Color.BLACK
                    canvas.drawRect(0f, height * 0.35f, width.toFloat(), height.toFloat(), paint)
                }
                paint.color = Color.WHITE; paint.textSize = 18f
                if (showLabel) canvas.drawText("$label display=${display?.displayId}", 10f, 24f, paint)
            }
        }
        setContentView(pattern)
        apply(intent)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); apply(intent) }
    override fun onDestroy() { stopTone(); super.onDestroy() }
    private fun apply(input: Intent) {
        leftColor = runCatching { Color.parseColor(input.getStringExtra("left") ?: "#ff0000") }.getOrDefault(Color.RED)
        rightColor = runCatching { Color.parseColor(input.getStringExtra("right") ?: "#0000ff") }.getOrDefault(Color.BLUE)
        label = input.getStringExtra("label")?.take(30) ?: "FIXTURE"
        showLabel = !input.getBooleanExtra("hide_label", false)
        darkHudScene = input.getBooleanExtra("dark_hud_scene", false)
        pattern.invalidate()
        stopTone()
        if (input.getBooleanExtra("tone", false)) {
            try {
                // Four-second loop, alternating amplitude each second; only app playback.
                val rate = 44100
                val data = ShortArray(rate * 4) { index ->
                    val amplitude = if ((index / rate) % 2 == 0) 2200 else 6600
                    (amplitude * sin(2 * PI * 440 * index / rate)).toInt().toShort()
                }
                track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(data.size * 2).build()
                track!!.let {
                        check(it.write(data, 0, data.size) == data.size)
                        it.setVolume(0.35f)
                        check(it.setLoopPoints(0, data.size, -1) == AudioTrack.SUCCESS)
                        it.play()
                    }
                main.postDelayed({ stopTone() }, input.getIntExtra("tone_seconds", 30).coerceIn(1, 120) * 1000L)
            } catch (e: Exception) { stopTone(); Log.w("RunelightFixture", "tone:${e.javaClass.simpleName}") }
        }
        Log.i("RunelightFixture", "pattern display=${display?.displayId} label=$label left=${Color.red(leftColor)},${Color.green(leftColor)},${Color.blue(leftColor)} right=${Color.red(rightColor)},${Color.green(rightColor)},${Color.blue(rightColor)} tone=${track != null}")
    }
    private fun stopTone() {
        main.removeCallbacksAndMessages(null)
        track?.let { runCatching { it.stop() }; runCatching { it.release() } }
        track = null
    }
}
