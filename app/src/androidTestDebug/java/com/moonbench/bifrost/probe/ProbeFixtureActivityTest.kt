package com.moonbench.bifrost.probe

import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.Display
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Guards the real pre-decor startup failure and fixture recreation, without capture. */
@RunWith(AndroidJUnit4::class)
class ProbeFixtureActivityTest {
    @Test
    fun splitFixtureLaunchesAndRecreatesOnAnActiveDisplay() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val displays = context.getSystemService(DisplayManager::class.java)
        val active = displays.displays.firstOrNull { it.state == Display.STATE_ON && it.displayId != Display.DEFAULT_DISPLAY }
            ?: displays.getDisplay(Display.DEFAULT_DISPLAY)
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = active.displayId }
        val intent = Intent(context, ProbeFixtureActivity::class.java)
            .putExtra("left", "#ff0000").putExtra("right", "#0000ff")
            .putExtra("label", "LAUNCH REGRESSION").putExtra("tone", false)
        ActivityScenario.launch<ProbeFixtureActivity>(intent, options.toBundle()).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            assertContentAttached(scenario)
            scenario.recreate()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            assertContentAttached(scenario)
        }
    }

    private fun assertContentAttached(scenario: ActivityScenario<ProbeFixtureActivity>) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario.onActivity { activity ->
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            assertEquals(1, content.childCount)
            val pattern: View = content.getChildAt(0)
            assertTrue(pattern.isAttachedToWindow)
            assertTrue(pattern.width > 0)
            assertTrue(pattern.height > 0)
            val rendered = Bitmap.createBitmap(pattern.width, pattern.height, Bitmap.Config.ARGB_8888)
            try {
                pattern.draw(Canvas(rendered))
                // Sample away from the identifying caption and split boundary.
                assertEquals(Color.RED, rendered.getPixel(pattern.width / 4, pattern.height / 2))
                assertEquals(Color.BLUE, rendered.getPixel(pattern.width * 3 / 4, pattern.height / 2))
            } finally { rendered.recycle() }
        }
    }
}
