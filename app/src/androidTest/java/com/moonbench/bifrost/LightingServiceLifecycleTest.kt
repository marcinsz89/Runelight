package com.moonbench.bifrost

import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.Display
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.services.LEDService
import com.moonbench.bifrost.services.LightingIntentStore
import com.moonbench.bifrost.services.LightingPhase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real service lifecycle and hardware route. Always ends with explicit Stop. */
@RunWith(AndroidJUnit4::class)
class LightingServiceLifecycleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun launch(): ActivityScenario<RunelightActivity> {
        val displays = context.getSystemService(DisplayManager::class.java)
        val display = displays.displays.firstOrNull { it.state == Display.STATE_ON && it.displayId != 0 }
            ?: displays.getDisplay(Display.DEFAULT_DISPLAY)
        return ActivityScenario.launch(Intent(context, RunelightActivity::class.java),
            ActivityOptions.makeBasic().apply { launchDisplayId = display.displayId }.toBundle())
    }

    private fun start(type: LedAnimationType) = Intent(context, LEDService::class.java)
        .setAction(LEDService.ACTION_START).putExtra("animationType", type.name).putExtra("animationColor", 0xff400080.toInt())
        .putExtra("animationRightColor", 0xff004080.toInt()).putExtra("speed", 0.8f)
        .putExtra("brightness", 80)
        // Deliberately omit the legacy background-run / boot / API switches.

    private fun stop() = LEDService.requestStop(context)

    private fun awaitMode(expected: LedAnimationType?) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (SystemClock.elapsedRealtime() < deadline) {
            var matches = false
            instrumentation.runOnMainSync { matches = LEDService.runningEffect == expected && LEDService.isRunning == (expected != null) }
            if (matches) return
            Thread.sleep(25)
        }
        fail("Expected $expected, actual ${LEDService.runningEffect}, running=${LEDService.isRunning}")
    }

    @Test fun rapidStopStartAndLateParameterUpdatesRespectTheLastCommand() {
        launch().use {
            try {
                context.startForegroundService(start(LedAnimationType.STATIC))
                awaitMode(LedAnimationType.STATIC)
                for (mode in listOf(LedAnimationType.RAINBOW, LedAnimationType.BREATH, LedAnimationType.PULSE, LedAnimationType.STATIC)) {
                    instrumentation.runOnMainSync {
                        stop()
                        context.startForegroundService(start(mode))
                    }
                    awaitMode(mode)
                    Thread.sleep(250) // Beyond the retired delayed-shutdown window.
                    assertEquals(mode, LEDService.runningEffect)
                    assertTrue(LEDService.isRunning)
                }
                instrumentation.runOnMainSync {
                    stop()
                    context.startService(start(LedAnimationType.RAINBOW).setAction(LEDService.ACTION_UPDATE_PARAMS))
                }
                awaitMode(null)
                Thread.sleep(250)
                assertFalse(LEDService.isRunning)
                assertNull(LEDService.runningEffect)
            } finally { stop(); awaitMode(null) }
        }
    }

    @Test fun removingTheUiTaskDoesNotStopLightingWithoutLegacyFlags() {
        launch().use { scenario ->
            try {
                context.startForegroundService(start(LedAnimationType.STATIC))
                awaitMode(LedAnimationType.STATIC)
                scenario.onActivity { it.finishAndRemoveTask() }
                Thread.sleep(2300) // Beyond the retired two-second task-existence check.
                assertTrue(LEDService.isRunning)
                assertEquals(LedAnimationType.STATIC, LEDService.runningEffect)
            } finally { stop(); awaitMode(null) }
        }
    }

    @Test fun stopWithoutAnInstanceClearsDurableIntentAndParameterUpdatesCannotRestartIt() {
        launch().use {
            try {
                stop()
                awaitMode(null)
                val store = LightingIntentStore(context)
                // Model retained intent after process loss, with no live effect.
                assertTrue(store.setWantedRunning(true))
                stop()
                instrumentation.waitForIdleSync()
                assertFalse(LightingIntentStore(context).wantedRunning)
                context.startService(start(LedAnimationType.RAINBOW).setAction(LEDService.ACTION_UPDATE_PARAMS))
                Thread.sleep(300)
                assertFalse(LEDService.isRunning)
                assertEquals(LightingPhase.STOPPED, LEDService.state.value?.phase)
            } finally { stop(); awaitMode(null) }
        }
    }

    @Test fun legacyPoliciesAndCommandsCannotReplaceAnExplicitEffect() {
        val prefs = context.getSharedPreferences("bifrost_prefs", android.content.Context.MODE_PRIVATE)
        val original = prefs.all
        launch().use {
            try {
                prefs.edit().putBoolean("auto_switch_enabled", true).commit()
                context.startForegroundService(start(LedAnimationType.STATIC)
                    .putExtra(LEDService.EXTRA_BATTERY_OVERRIDE_WHEN_PLUGGED, true)
                    .putExtra(LEDService.EXTRA_LOW_BATTERY_ALERT_ENABLED, true)
                    .putExtra(LEDService.EXTRA_LOW_BATTERY_ALERT_THRESHOLD, 100)
                    .putExtra(LEDService.EXTRA_ADAPTIVE_BRIGHTNESS, true))
                awaitMode(LedAnimationType.STATIC)
                assertTrue(LightingIntentStore(context).wantedRunning)
                for (action in listOf(LEDService.ACTION_FORCE_APP_PROFILE_RESOLUTION,
                    LEDService.ACTION_EXTERNAL_DISPLAY, LEDService.ACTION_SUPPLY_PROJECTION)) {
                    context.startService(Intent(context, LEDService::class.java).setAction(action)
                        .putExtra("animationType", LedAnimationType.PULSE.name))
                }
                Thread.sleep(2300)
                assertEquals(LedAnimationType.STATIC, LEDService.runningEffect)
                stop()
                awaitMode(null)
                assertFalse(LightingIntentStore(context).wantedRunning)
                // Earlier boot/startup helpers used a null action, even with a preset.
                context.startForegroundService(start(LedAnimationType.RAINBOW).setAction(null))
                Thread.sleep(300)
                assertFalse(LEDService.isRunning)
                assertFalse(LightingIntentStore(context).wantedRunning)
            } finally {
                stop(); awaitMode(null)
                prefs.edit().clear().apply {
                    for ((key, value) in original) when (value) {
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is Set<*> -> { @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>) }
                    }
                }.commit()
            }
        }
    }

    @Test fun destructionPreservesIntentButRecreatingTheUiDoesNotRestartLighting() {
        launch().use { scenario ->
            try {
                context.startForegroundService(start(LedAnimationType.STATIC))
                awaitMode(LedAnimationType.STATIC)
                // Destruction is distinct from the explicit Stop command.
                context.stopService(Intent(context, LEDService::class.java))
                awaitMode(null)
                assertTrue(LightingIntentStore(context).wantedRunning)
                scenario.recreate()
                Thread.sleep(300)
                assertFalse(LEDService.isRunning)
                assertTrue(LightingIntentStore(context).wantedRunning)
                stop()
                awaitMode(null)
                assertFalse(LightingIntentStore(context).wantedRunning)
            } finally { stop(); awaitMode(null) }
        }
    }
}
