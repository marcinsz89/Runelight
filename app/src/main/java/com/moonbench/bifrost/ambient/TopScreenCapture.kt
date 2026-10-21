package com.moonbench.bifrost.ambient

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.view.Display
import android.view.WindowManager
import kotlin.math.roundToInt

internal interface AmbientCapture {
    fun start(resultCode: Int, grant: Intent)
    fun close()
}

/** Main-looper-owned top capture. No window, persisted grant, or automatic restart. */
internal class TopScreenCapture(
    private val context: Context,
    private val handler: Handler,
    private val onFrame: (IntArray, Int, Int) -> Unit,
    private val onFailure: () -> Unit
) : AmbientCapture {
    private val displays = context.getSystemService(DisplayManager::class.java)
    private var projection: MediaProjection? = null
    private var output: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var active = false
    val mediaProjection: MediaProjection? get() = projection.takeIf { active }
    private var videoEnabled = false
    private var videoOwner = 0L
    private var frameConsumer: ((IntArray, Int, Int) -> Unit)? = null
    private var lastSampleAt = Long.MIN_VALUE
    private var density = 0
    private var width = 0
    private var height = 0
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { fail() }
        override fun onCapturedContentResize(width: Int, height: Int) {
            if (active && videoEnabled) runCatching { resize(width, height) }.onFailure { fail() }
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) { if (displayId == Display.DEFAULT_DISPLAY) fail() }
        override fun onDisplayChanged(displayId: Int) {
            // API 34+ reports captured-content sizes. On Thor 13 use the source
            // display context, never the Activity's lower-panel configuration.
            if (active && videoEnabled && displayId == Display.DEFAULT_DISPLAY && Build.VERSION.SDK_INT < 34)
                runCatching { sourceBounds().let { resize(it.first, it.second) } }.onFailure { fail() }
        }
    }

    override fun start(resultCode: Int, grant: Intent) {
        acquire(resultCode, grant)
        if (active) resumeVideo(onFrame)
    }

    /** Acquire once; audio-only sessions need no virtual display or image reader. */
    fun acquire(resultCode: Int, grant: Intent) {
        check(!active && projection == null)
        active = true
        try {
            projection = context.getSystemService(MediaProjectionManager::class.java)
                .getMediaProjection(resultCode, grant) ?: error("Capture consent unavailable")
            projection!!.registerCallback(projectionCallback, handler)
            displays.registerDisplayListener(displayListener, handler)
        } catch (_: RuntimeException) { fail() }
    }

    /** Effect leases video only. Closing a lease never stops shared audio/consent. */
    fun videoLease(consumer: (IntArray, Int, Int) -> Unit): AmbientCapture = object : AmbientCapture {
        private var owner = -1L
        override fun start(resultCode: Int, grant: Intent) { owner = resumeVideo(consumer) }
        override fun close() { if (owner == videoOwner) pauseVideo() }
    }

    private fun resumeVideo(consumer: (IntArray, Int, Int) -> Unit): Long {
        check(active && !videoEnabled)
        videoOwner++
        videoEnabled = true
        frameConsumer = consumer
        try {
            val bounds = sourceBounds()
            val size = grid(bounds.first, bounds.second)
            width = size.first; height = size.second
            reader = createReader(width, height)
            lastSampleAt = Long.MIN_VALUE
            if (output == null) {
                output = projection!!.createVirtualDisplay("Runelight Ambient", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler)
                    ?: error("Capture display unavailable")
            } else {
                // Android 14+ grants permit only one createVirtualDisplay call.
                output!!.resize(width, height, density)
                output!!.surface = reader!!.surface
            }
        } catch (_: RuntimeException) { fail() }
        return videoOwner
    }

    private fun pauseVideo() {
        videoOwner++
        videoEnabled = false
        frameConsumer = null
        output?.surface = null
        reader?.setOnImageAvailableListener(null, null)
        reader?.close(); reader = null
    }

    private fun sourceBounds(): Pair<Int, Int> {
        val display = displays.getDisplay(Display.DEFAULT_DISPLAY) ?: error("Top screen unavailable")
        val displayContext = context.createDisplayContext(display)
        val windowContext = displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION, null)
        val bounds = windowContext.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        density = displayContext.resources.configuration.densityDpi
        require(bounds.width() > 0 && bounds.height() > 0 && density > 0)
        return bounds.width() to bounds.height()
    }

    private fun grid(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> {
        require(sourceWidth > 0 && sourceHeight > 0)
        // Bound both axes while retaining source aspect, including portrait.
        val scale = 64.0 / maxOf(sourceWidth, sourceHeight)
        return (sourceWidth * scale).roundToInt().coerceAtLeast(2) to
            (sourceHeight * scale).roundToInt().coerceAtLeast(1)
    }

    private fun createReader(w: Int, h: Int): ImageReader =
        ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2).also { newReader ->
            newReader.setOnImageAvailableListener({ source ->
                if (!active || source !== reader) return@setOnImageAvailableListener
                try {
                    source.acquireLatestImage()?.use { image ->
                        val now = SystemClock.elapsedRealtime()
                        // Follow scene changes promptly; the shared writer owns
                        // the independent 25 Hz hardware limit.
                        if (lastSampleAt != Long.MIN_VALUE && now - lastSampleAt < 40) return@use
                        val crop = image.cropRect
                        val plane = image.planes[0]
                        val pixels = AmbientSampling.rgba(plane.buffer, plane.rowStride, plane.pixelStride,
                            crop.left, crop.top, crop.width(), crop.height())
                        lastSampleAt = now
                        frameConsumer?.invoke(pixels, crop.width(), crop.height())
                    }
                } catch (_: RuntimeException) { fail() }
            }, handler)
        }

    private fun resize(sourceWidth: Int, sourceHeight: Int) {
        val (w, h) = grid(sourceWidth, sourceHeight)
        if (w == width && h == height) return
        val replacement = createReader(w, h)
        val old = reader
        reader = replacement
        try {
            output?.surface = replacement.surface
            output?.resize(w, h, density)
            width = w; height = h
            lastSampleAt = Long.MIN_VALUE
        } finally {
            old?.setOnImageAvailableListener(null, null)
            old?.close()
        }
    }

    private fun fail() {
        if (!active) return
        close()
        onFailure()
    }

    override fun close() {
        active = false // Fence queued listeners and onStop before releasing.
        videoOwner++
        videoEnabled = false
        frameConsumer = null
        displays.unregisterDisplayListener(displayListener)
        reader?.setOnImageAvailableListener(null, null)
        runCatching { output?.release() }; output = null
        runCatching { reader?.close() }; reader = null
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }; projection = null
    }
}
