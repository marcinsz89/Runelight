package com.moonbench.bifrost.probe

import android.Manifest
import android.content.pm.PackageManager

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import android.view.Display
import android.view.WindowManager
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

/** Isolated bounded diagnostic. All runtime state/resources are confined to worker. */
class CaptureProbeService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("RunelightProbe")
    private lateinit var worker: Handler
    @Volatile private var latestStartId = 0
    private var sessionStartId = 0
    private var generation = 0L
    private var active = false
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: AudioRecord? = null
    private var audioEpoch = 0L
    private var state = JSONObject()
    private var lastPublish = 0L
    private var frames = 0L
    private var startedAt = 0L
    private var previousFrameAt = 0L
    private var audioReads = 0L
    private var lastAudioAt = 0L
    private var cadence = 500L

    override fun onCreate() {
        super.onCreate()
        thread.start()
        worker = Handler(thread.looper)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("capture-probe", "Debug capture probe", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(startId); return START_NOT_STICKY }
        latestStartId = startId
        val command = intent.getStringExtra("command")
        if (command !in listOf("stop", "audio_off")) {
            try {
                val stop = PendingIntent.getService(this, 90, Intent(this, CaptureProbeService::class.java).putExtra("command", "stop"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val notification = Notification.Builder(this, "capture-probe").setContentTitle("Runelight debug capture probe")
                    .setContentText("Aggregate samples only • bounded session").setSmallIcon(android.R.drawable.ic_menu_view)
                    .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
                val type = if (intent.hasExtra("grant")) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
                startForeground(90, notification, type)
            } catch (e: Exception) {
                worker.post { sessionStartId = startId; finish("failed", "foreground:${e.javaClass.simpleName}") }
                return START_NOT_STICKY
            }
        }
        worker.post {
            sessionStartId = startId
            when (command) {
                "stop" -> if (active) finish("stopped", "explicit_stop") else stopSelf(startId)
                "audio_off" -> if (active) { stopAudio(); state.put("audio_state", "off"); publish(true) } else stopSelf(startId)
                "projection", "accessibility", "snapshot" -> start(intent)
                else -> finish("failed", "unknown_command")
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null
    override fun onDestroy() {
        worker.post { release(); thread.quitSafely() }
        super.onDestroy()
    }

    private fun start(intent: Intent) {
        release()
        active = true
        val token = generation
        frames = 0; audioReads = 0; previousFrameAt = 0; lastAudioAt = 0
        startedAt = SystemClock.elapsedRealtime()
        cadence = intent.getIntExtra("cadence_ms", 500).coerceIn(250, 5000).toLong()
        val source = intent.getIntExtra("source_display", Display.DEFAULT_DISPLAY)
        val backend = intent.getStringExtra("command")!!
        state = JSONObject().put("generation", token).put("state", "starting").put("backend", backend)
            .put("requested_display", source).put("source_semantics", if (backend == "projection") "OS-selected projection; display ID only supplies dimensions" else "Accessibility explicit logical display ID")
            .put("audio_state", "off").put("frames", 0).put("started_elapsed_ms", startedAt)
        publish(true)
        try {
            val display = getSystemService(DisplayManager::class.java).getDisplay(source) ?: error("display_unavailable")
            val rawMetrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") display.getRealMetrics(rawMetrics)
            // Thor can return the caller's panel configuration from getRealMetrics.
            // A window context explicitly bound to the requested display owns its bounds.
            val displayContext = createDisplayContext(display)
            val windowContext = displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION, null)
            val bounds = windowContext.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            val sourceWidth = bounds.width()
            val sourceHeight = bounds.height()
            val density = displayContext.resources.displayMetrics.densityDpi
            check(sourceWidth > 0 && sourceHeight > 0 && density > 0) { "invalid_display_bounds" }
            state.put("source_width", sourceWidth).put("source_height", sourceHeight)
                .put("dimensions_source", "requested_display_window_maximum_metrics")
                .put("raw_real_width", rawMetrics.widthPixels).put("raw_real_height", rawMetrics.heightPixels)
                .put("density_dpi", density)
            if (intent.hasExtra("grant")) {
                val grant = intent.getParcelableExtra("grant", Intent::class.java) ?: error("missing_grant")
                projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent.getIntExtra("grant_code", 0), grant) ?: error("projection_unavailable")
                projectionCallback = object : MediaProjection.Callback() {
                    override fun onStop() { if (active && token == generation) finish("failed", "projection_revoked") }
                }.also { projection!!.registerCallback(it, worker) }
            }
            if (backend == "projection") {
                val session = projection ?: error("fresh_consent_required")
                val width = 64
                val height = (sourceHeight.toDouble() / sourceWidth * width).toInt().coerceIn(1, 256)
                state.put("sample_width", width).put("sample_height", height)
                reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { output ->
                    output.setOnImageAvailableListener({ imageReader ->
                        if (token != generation || !active) return@setOnImageAvailableListener
                        try {
                            imageReader.acquireLatestImage()?.use { image ->
                                val plane = image.planes[0]
                                val pixels = IntArray(image.width * image.height)
                                for (y in 0 until image.height) for (x in 0 until image.width) {
                                    val index = y * plane.rowStride + x * plane.pixelStride
                                    if (index + 2 >= plane.buffer.limit()) error("invalid_image_stride")
                                    pixels[y * image.width + x] = Color.rgb(plane.buffer.get(index).toInt() and 255, plane.buffer.get(index + 1).toInt() and 255, plane.buffer.get(index + 2).toInt() and 255)
                                }
                                sample(pixels, image.width, image.height, image.timestamp, token)
                            }
                        } catch (e: Exception) { if (token == generation) finish("failed", "frame:${e.javaClass.simpleName}") }
                    }, worker)
                }
                virtualDisplay = session.createVirtualDisplay("RunelightProbe", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, worker)
            } else screenshot(source, token, backend == "snapshot")
            if (intent.getBooleanExtra("audio", false)) startAudio(token, intent.getIntExtra("audio_uid", -1))
            worker.postDelayed({ if (active && token == generation && frames == 0L) finish("failed", "first_sample_timeout") }, 8000)
            val duration = intent.getIntExtra("duration_s", 120).coerceIn(5, 600)
            state.put("duration_s", duration)
            worker.postDelayed({ if (active && token == generation) finish("stopped", "duration_limit") }, duration * 1000L)
        } catch (e: Exception) { finish("failed", "startup:${e.javaClass.simpleName}:${e.message?.take(100)}") }
    }

    private fun screenshot(display: Int, token: Long, once: Boolean) {
        if (!active || token != generation) return
        val access = ProbeAccessibilityService.connected
        if (access == null) { finish("failed", "debug_accessibility_disconnected"); return }
        var complete = false
        worker.postDelayed({ if (!complete && active && token == generation) finish("failed", "screenshot_callback_timeout") }, 8000)
        access.capture(display, worker) { bitmap, timestamp, error ->
            complete = true
            if (token != generation || !active) { bitmap?.recycle(); return@capture }
            if (error != null || bitmap == null) { finish("failed", error ?: "empty_screenshot"); return@capture }
            try {
                val width = 64
                val height = (bitmap.height.toDouble() / bitmap.width * width).toInt().coerceIn(1, 256)
                val small = Bitmap.createScaledBitmap(bitmap, width, height, true)
                try {
                    val pixels = IntArray(width * height)
                    small.getPixels(pixels, 0, width, 0, 0, width, height)
                    state.put("sample_width", width).put("sample_height", height)
                    sample(pixels, width, height, timestamp, token)
                } finally { if (small !== bitmap) small.recycle() }
                if (once) finish("stopped", "snapshot_complete")
                else worker.postDelayed({ screenshot(display, token, false) }, cadence)
            } catch (e: Exception) { finish("failed", "screenshot_sample:${e.javaClass.simpleName}") }
            finally { bitmap.recycle() }
        }
    }

    private fun sample(pixels: IntArray, width: Int, height: Int, timestamp: Long, token: Long) {
        if (!active || token != generation) return
        val sum = Array(2) { LongArray(4) }
        pixels.forEachIndexed { index, color ->
            val side = if (index % width < width / 2) 0 else 1
            sum[side][0] += Color.red(color); sum[side][1] += Color.green(color); sum[side][2] += Color.blue(color); sum[side][3]++
        }
        fun rgb(side: Int) = org.json.JSONArray((0..2).map { (sum[side][it] / sum[side][3]).toInt() })
        val now = SystemClock.elapsedRealtime()
        frames++
        state.put("state", "running").put("frames", frames).put("left_rgb", rgb(0)).put("right_rgb", rgb(1))
            .put("frame_timestamp_ns", timestamp).put("last_frame_elapsed_ms", now)
            .put("last_frame_interval_ms", if (previousFrameAt == 0L) 0 else now - previousFrameAt)
            .put("first_frame_latency_ms", if (frames == 1L) now - startedAt else state.optLong("first_frame_latency_ms"))
            .put("measured_fps", frames * 1000.0 / (now - startedAt).coerceAtLeast(1))
        previousFrameAt = now
        publish(frames == 1L)
    }

    private fun startAudio(token: Long, sourceUid: Int) {
        try {
            check(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                "record_audio_permission_missing"
            }
            val session = projection ?: error("audio_requires_projection_consent")
            val config = AudioPlaybackCaptureConfiguration.Builder(session)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME)
                .apply { if (sourceUid >= 0) addMatchingUid(sourceUid) }.build()
            state.put("audio_match_uid", sourceUid)
            val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
            recorder = AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(16000)
                .setAudioPlaybackCaptureConfig(config).build()
            recorder!!.startRecording()
            state.put("audio_state", "starting"); publish(true)
            val epoch = ++audioEpoch
            val buffer = ShortArray(4096)
            fun poll() {
                if (token != generation || epoch != audioEpoch || !active) return
                try {
                    val count = recorder?.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING) ?: return
                    if (count < 0) error("read_$count")
                    if (count > 0) {
                        var squares = 0.0; var peak = 0
                        for (index in 0 until count) { val value = buffer[index].toInt(); squares += value.toDouble() * value; peak = maxOf(peak, kotlin.math.abs(value)) }
                        audioReads++; lastAudioAt = SystemClock.elapsedRealtime()
                        state.put("audio_state", "reading").put("audio_reads", audioReads).put("audio_rms", sqrt(squares / count) / 32768.0)
                            .put("audio_peak", peak / 32768.0).put("last_audio_elapsed_ms", lastAudioAt)
                        publish(false)
                    } else if (SystemClock.elapsedRealtime() - (if (lastAudioAt == 0L) startedAt else lastAudioAt) > 5000) error("no_samples_timeout")
                    worker.postDelayed({ poll() }, 50)
                } catch (e: Exception) { stopAudio(); state.put("audio_state", "failed").put("audio_error", e.message?.take(100)); publish(true) }
            }
            poll()
        } catch (e: Exception) { stopAudio(); state.put("audio_state", "failed").put("audio_error", "${e.javaClass.simpleName}:${e.message?.take(100)}"); publish(true) }
    }

    private fun stopAudio() {
        audioEpoch++
        recorder?.let { runCatching { it.stop() }; runCatching { it.release() } }
        recorder = null
    }

    private fun release() {
        generation = SystemClock.elapsedRealtimeNanos()
        active = false
        stopAudio()
        reader?.setOnImageAvailableListener(null, null)
        runCatching { virtualDisplay?.release() }; virtualDisplay = null
        runCatching { reader?.close() }; reader = null
        projectionCallback?.let { callback -> runCatching { projection?.unregisterCallback(callback) } }; projectionCallback = null
        runCatching { projection?.stop() }; projection = null
    }

    private fun finish(result: String, reason: String) {
        release()
        state.put("state", result).put("reason", reason).put("finished_elapsed_ms", SystemClock.elapsedRealtime())
        if (state.optString("audio_state") in listOf("starting", "reading")) state.put("audio_state", "stopped")
        publish(true)
        val endingStartId = sessionStartId
        main.post {
            if (endingStartId == latestStartId) stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(endingStartId)
        }
    }

    private fun publish(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPublish < 1000) return
        lastPublish = now
        state.put("updated_elapsed_ms", now)
        val json = state.toString()
        // Only aggregate values; atomic replacement keeps Activity/ADB reads coherent.
        runCatching {
            val temporary = File(filesDir, "probe-status.tmp")
            temporary.writeText(json)
            check(temporary.renameTo(File(filesDir, "probe-status.json")))
        }.onFailure { Log.w("RunelightProbe", "status_write:${it.javaClass.simpleName}") }
        Log.i("RunelightProbe", json)
    }
}
