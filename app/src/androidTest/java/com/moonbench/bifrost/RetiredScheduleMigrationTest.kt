package com.moonbench.bifrost

import android.app.PendingIntent
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RetiredScheduleMigrationTest {
    @Test fun removesTheEarlierAlarmTokenAndDoesNotRecreateIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Historical identity is deliberate: it must match already-installed builds.
        val intent = Intent("com.moonbench.bifrost.action.SCHEDULE_TICK")
            .setClassName(context.packageName, "com.moonbench.bifrost.receivers.ScheduleReceiver")
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        val old = PendingIntent.getBroadcast(context, 8421, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            assertNotNull(PendingIntent.getBroadcast(context, 8421, intent, flags))
            BifrostApp.cancelRetiredSchedule(context)
            assertNull(PendingIntent.getBroadcast(context, 8421, intent, flags))
            BifrostApp.cancelRetiredSchedule(context)
            assertNull(PendingIntent.getBroadcast(context, 8421, intent, flags))
        } finally {
            old.cancel()
        }
    }
}
