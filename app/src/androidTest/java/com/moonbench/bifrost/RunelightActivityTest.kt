package com.moonbench.bifrost

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.Display
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            scenario.onActivity { it.findViewById<MaterialButton>(R.id.quickLeftColor).performClick() }
            scenario.onActivity { it.findViewById<Slider>(R.id.quickRed).value = 10f }
            scenario.recreate()
            onView(withId(R.id.lightingTile)).check(matches(isDisplayed()))
            scenario.onActivity { assertEquals(10f, it.findViewById<Slider>(R.id.quickRed).value) }
            assertFalse(prefs.contains("leftColor"))
            assertFalse(LEDService.isRunning)
        }
    }

}
