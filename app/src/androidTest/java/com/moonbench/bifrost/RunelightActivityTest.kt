package com.moonbench.bifrost

import android.app.ActivityOptions
import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.graphics.Color
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.services.LightingPhase
import com.moonbench.bifrost.services.LightingState
import androidx.lifecycle.Observer
import com.google.android.material.button.MaterialButton
import com.moonbench.bifrost.ui.ColorLevelTile
import com.skydoves.colorpickerview.ColorPickerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.moonbench.bifrost.animations.LedAnimationType
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunelightActivityTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun launchOnActiveDisplay(): ActivityScenario<RunelightActivity> {
        // The Thor can keep display 0 OFF while display 4 is interactive. An
        // Espresso launch on the default screen then cannot regain window focus.
        val activeDisplay = context.getSystemService(DisplayManager::class.java).displays
            .firstOrNull { it.state == Display.STATE_ON && it.displayId != Display.DEFAULT_DISPLAY }
            ?: context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = activeDisplay.displayId }
        return ActivityScenario.launch(Intent(context, RunelightActivity::class.java), options.toBundle())
    }

    @Before
    fun resetTestControls() {
        context.stopService(Intent(context, LEDService::class.java))
        context.getSharedPreferences("runelight_controls", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun soundPreferenceStaysOnWhileBreathingAndPulseDisableTheTile() {
        val prefs = context.getSharedPreferences("runelight_controls", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("soundEnabled", true).commit()
        launchOnActiveDisplay().use { scenario ->
            for (id in listOf(R.id.effectBreathing, R.id.effectPulse)) {
                scenario.onActivity {
                    it.findViewById<MaterialButton>(id).performClick()
                    assertFalse(it.findViewById<View>(R.id.soundTile).isEnabled)
                    assertEquals("On", it.findViewById<android.widget.TextView>(R.id.soundState).text.toString())
                    assertTrue(prefs.getBoolean("soundEnabled", false))
                }
            }
            scenario.recreate()
            scenario.onActivity {
                assertFalse(it.findViewById<View>(R.id.soundTile).isEnabled)
                it.findViewById<MaterialButton>(R.id.effectRainbow).performClick()
            }
            scenario.onActivity { assertTrue(it.findViewById<View>(R.id.soundTile).isEnabled) }
            assertTrue(prefs.getBoolean("soundEnabled", false))
            assertFalse(LEDService.isRunning)
        }
    }

    @Test
    fun ambientSamplingSurvivesRecreationWithoutStartingOrRequestingCapture() {
        launchOnActiveDisplay().use { scenario ->
            scenario.onActivity {
                it.findViewById<MaterialButton>(R.id.effectAmbient).performClick()
                assertTrue("Edges is an active sampling mode", it.findViewById<View>(R.id.quickAmbientSampling).isActivated)
                it.findViewById<View>(R.id.quickAmbientSampling).performClick()
            }
            scenario.recreate()
            scenario.onActivity {
                assertEquals("Screen halves", it.findViewById<android.widget.TextView>(R.id.quickAmbientSamplingState).text.toString())
                assertEquals(View.GONE, it.findViewById<View>(R.id.quickSpeed).visibility)
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.quickAmbientSampling).visibility)
            }
            assertEquals("AMBIENT", context.getSharedPreferences("runelight_controls", Context.MODE_PRIVATE).getString("effect", null))
            assertFalse(LEDService.isRunning)
        }
    }

    @Test
    fun selectingEffectAndRecreatingDoesNotStartLighting() {
        launchOnActiveDisplay().use { scenario ->
            // Espresso 3.5's click injection targets display 0 on this firmware.
            // Exercise the real UI callback on its owning thread; the ADB smoke
            // run separately checks touch input with an explicit display ID.
            scenario.onActivity { it.findViewById<MaterialButton>(R.id.effectRainbow).performClick() }
            onView(withId(R.id.effectRainbow)).check(matches(isChecked()))
            scenario.recreate()
            onView(withId(R.id.effectRainbow)).check(matches(isChecked()))
            onView(withId(R.id.lightingState)).check(matches(withText(R.string.runelight_stopped)))
            assertFalse(LEDService.isRunning)
        }
    }

    @Test
    fun draftColorSurvivesRecreationWithoutBeingApplied() {
        val prefs = context.getSharedPreferences("runelight_controls", Context.MODE_PRIVATE)
        launchOnActiveDisplay().use { scenario ->
            scenario.onActivity { it.findViewById<ColorLevelTile>(R.id.quickLeftColor).performClick() }
            scenario.onActivity { it.findViewById<ColorPickerView>(R.id.quickColorPicker).fireColorListener(Color.CYAN, true) }
            scenario.recreate()
            onView(withId(R.id.lightingTile)).check(matches(isDisplayed()))
            onView(withId(R.id.quickColorHex)).check(matches(withText("#00FFFF")))
            onView(withId(R.id.quickApplyColor)).check(matches(isDisplayed()))
            assertFalse(prefs.contains("leftColor"))
            assertFalse(LEDService.isRunning)
        }
    }

    @Test
    fun independentLevelsPersistWithoutStartingLighting() {
        launchOnActiveDisplay().use { scenario ->
            scenario.onActivity { activity ->
                for ((id, value) in listOf(R.id.quickLeftColor to 20, R.id.quickRightColor to 80)) {
                    val args = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value.toFloat()) }
                    activity.findViewById<ColorLevelTile>(id).performAccessibilityAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args)
                }
            }
            scenario.recreate()
            scenario.onActivity {
                assertEquals(20, it.findViewById<ColorLevelTile>(R.id.quickLeftColor).level)
                assertEquals(80, it.findViewById<ColorLevelTile>(R.id.quickRightColor).level)
            }
            assertFalse(LEDService.isRunning)
        }
    }

    @Test
    fun centerSpeedTileIsModeSpecificAndRetainsItsOwnValue() {
        launchOnActiveDisplay().use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.quickSpeed).visibility)
                activity.findViewById<MaterialButton>(R.id.effectBreathing).performClick()
                val tile = activity.findViewById<ColorLevelTile>(R.id.quickSpeed)
                assertEquals(View.VISIBLE, tile.visibility)
                tile.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                    Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 75f) })
                assertEquals(40, activity.findViewById<ColorLevelTile>(R.id.quickLeftColor).level)
                assertEquals(40, activity.findViewById<ColorLevelTile>(R.id.quickRightColor).level)
            }
            scenario.recreate()
            scenario.onActivity { assertEquals(75, it.findViewById<ColorLevelTile>(R.id.quickSpeed).level) }
            assertFalse(LEDService.isRunning)
        }
    }

    @Test
    fun runningModeChangesReachTheRequestedAnimationAndStop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        fun awaitMode(mode: LedAnimationType?) {
            val deadline = android.os.SystemClock.elapsedRealtime() + 5000
            var matched = false
            while (android.os.SystemClock.elapsedRealtime() < deadline && !matched) {
                instrumentation.runOnMainSync {
                    matched = if (mode == null) !LEDService.isRunning else LEDService.isRunning && LEDService.runningEffect == mode
                }
                if (!matched) Thread.sleep(25)
            }
            assertTrue("Animation did not reach $mode", matched)
        }
        val handoffPhases = mutableListOf<LightingPhase>()
        val observer = Observer<LightingState> { handoffPhases.add(it.phase) }
        launchOnActiveDisplay().use { scenario ->
            try {
                scenario.onActivity { it.findViewById<View>(R.id.lightingTile).performClick() }
                awaitMode(LedAnimationType.STATIC)
                instrumentation.runOnMainSync { LEDService.state.observeForever(observer) }
                for ((button, mode) in listOf(
                    R.id.effectRainbow to LedAnimationType.RAINBOW,
                    R.id.effectBreathing to LedAnimationType.BREATH,
                    R.id.effectPulse to LedAnimationType.PULSE,
                    R.id.effectSolid to LedAnimationType.STATIC
                )) {
                    scenario.onActivity { it.findViewById<MaterialButton>(button).performClick() }
                    awaitMode(mode)
                }
                scenario.onActivity { it.findViewById<MaterialButton>(R.id.appearanceButton).performClick() }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertEquals(it.getString(R.string.runelight_on),
                        it.findViewById<android.widget.TextView>(R.id.lightingState).text.toString())
                    assertTrue(it.findViewById<View>(R.id.lightingTile).isActivated)
                }
                instrumentation.runOnMainSync {
                    assertTrue("A mode handoff blinked the acknowledged On state: $handoffPhases",
                        handoffPhases.isNotEmpty() && handoffPhases.all { it == LightingPhase.RUNNING })
                }
            } finally {
                instrumentation.runOnMainSync { LEDService.state.removeObserver(observer) }
                context.stopService(Intent(context, LEDService::class.java))
                awaitMode(null)
            }
        }
    }

    @Test
    fun verticalDragChangesOnlyItsSideWithoutOpeningColorEditor() {
        launchOnActiveDisplay().use { scenario ->
            scenario.onActivity { activity ->
                val tile = activity.findViewById<ColorLevelTile>(R.id.quickLeftColor)
                val now = android.os.SystemClock.uptimeMillis()
                for ((action, fraction) in listOf(MotionEvent.ACTION_DOWN to 0.75f, MotionEvent.ACTION_MOVE to 0.5f, MotionEvent.ACTION_UP to 0.5f)) {
                    val event = MotionEvent.obtain(now, now + 20, action, tile.width / 2f, tile.height * fraction, 0)
                    try { tile.dispatchTouchEvent(event) } finally { event.recycle() }
                }
                assertEquals(65, tile.level)
                assertEquals(40, activity.findViewById<ColorLevelTile>(R.id.quickRightColor).level)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.quickColorEditor).visibility)
            }
            assertFalse(LEDService.isRunning)
        }
    }

    @Test
    fun copiedColorAndLevelStayDraftUntilApplyAndSurviveRecreation() {
        val prefs = context.getSharedPreferences("runelight_controls", Context.MODE_PRIVATE)
        launchOnActiveDisplay().use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<ColorLevelTile>(R.id.quickRightColor).performAccessibilityAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                    Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 80f) })
                activity.findViewById<ColorLevelTile>(R.id.quickLeftColor).performClick()
                activity.findViewById<MaterialButton>(R.id.quickCopyOther).performClick()
                assertEquals(40, prefs.getInt("leftBrightness", -1))
                activity.findViewById<MaterialButton>(R.id.quickCancelColor).performClick()
                assertEquals(40, activity.findViewById<ColorLevelTile>(R.id.quickLeftColor).level)
                activity.findViewById<ColorLevelTile>(R.id.quickLeftColor).performClick()
                activity.findViewById<MaterialButton>(R.id.quickCopyOther).performClick()
            }
            scenario.recreate()
            onView(withId(R.id.quickColorLevel)).check(matches(withText("80%")))
            scenario.onActivity { it.findViewById<MaterialButton>(R.id.quickApplyColor).performClick() }
            assertEquals(prefs.getInt("rightColor", 0), prefs.getInt("leftColor", 1))
            assertEquals(80, prefs.getInt("leftBrightness", -1))
            assertEquals(80, prefs.getInt("rightBrightness", -1))
            assertFalse(LEDService.isRunning)
        }
    }

}
