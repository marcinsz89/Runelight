package com.moonbench.bifrost

import android.app.Notification
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moonbench.bifrost.animations.LedAnimationType
import com.moonbench.bifrost.audio.SoundPhase
import com.moonbench.bifrost.audio.SoundState
import com.moonbench.bifrost.services.*
import com.moonbench.bifrost.ui.RunelightShortcuts
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Framework construction checks; does not acquire capture or write LEDs. */
@RunWith(AndroidJUnit4::class)
class LightingNotificationsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun activeNoticeHasDirectImmutableControlsAndStopActions() {
        val notice = LightingNotice.from(LightingState(LightingPhase.RUNNING, LedAnimationType.AMBIENT),
            SoundState(SoundPhase.ACTIVE, true))!!
        val notification = LightingNotifications.build(context, notice)
        assertEquals(context.getString(R.string.runelight_ambient), notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(context.getString(R.string.runelight_notice_sound_active), notification.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(1, notification.actions.size)
        assertTrue(notification.actions.single().actionIntent.isService)
        assertTrue(notification.actions.single().actionIntent.isImmutable)
        assertTrue(notification.contentIntent.isActivity)
        assertTrue(notification.contentIntent.isImmutable)
    }

    @Test fun captureLossNoticeIsDismissibleAndCannotStartLightingFromAnAction() {
        val notice = LightingNotice.from(LightingState(LightingPhase.FAILED,
            LedAnimationType.AMBIENT, LightingFailure.CAPTURE_LOST), SoundState())!!
        val notification = LightingNotifications.build(context, notice)
        assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertTrue(notification.actions.isNullOrEmpty())
        assertTrue(notification.contentIntent.isActivity)
    }

    @Test fun shortcutOnlyOpensNativeControlsWithoutCaptureOrDisplayOverrides() {
        val intent = RunelightShortcuts.controlsIntent(context)
        assertEquals(RunelightActivity::class.java.name, intent.component!!.className)
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_LAUNCHER))
        assertNull(intent.extras)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            intent.flags)
    }
}
