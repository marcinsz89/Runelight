package com.moonbench.bifrost.probe

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Handler
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.Executors

/** Explicitly enabled debug-only screenshot provider, independent of legacy service. */
class ProbeAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var connected: ProbeAccessibilityService? = null; private set
        // Process-lifetime delivery ensures even a late result closes its hardware buffer.
        private val results = Executors.newSingleThreadExecutor { Thread(it, "RunelightProbeShot").apply { isDaemon = true } }
    }
    override fun onServiceConnected() { connected = this }
    override fun onDestroy() { if (connected === this) connected = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    fun capture(display: Int, handler: Handler, result: (Bitmap?, Long, String?) -> Unit) {
        fun deliver(bitmap: Bitmap?, timestamp: Long, error: String?) {
            if (!handler.post { result(bitmap, timestamp, error) }) bitmap?.recycle()
        }
        try {
            takeScreenshot(display, results, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val hardware = screenshot.hardwareBuffer
                    var wrapper: Bitmap? = null
                    var copy: Bitmap? = null
                    var error: String? = null
                    try {
                        wrapper = Bitmap.wrapHardwareBuffer(hardware, screenshot.colorSpace)
                        copy = wrapper?.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (e: Exception) { error = "screenshot_copy:${e.javaClass.simpleName}" }
                    finally { wrapper?.recycle(); hardware.close() }
                    deliver(copy, screenshot.timestamp * 1_000_000, error)
                }
                override fun onFailure(errorCode: Int) { deliver(null, 0, "accessibility_error_$errorCode") }
            })
        } catch (e: Exception) { deliver(null, 0, "screenshot_request:${e.javaClass.simpleName}") }
    }
}
