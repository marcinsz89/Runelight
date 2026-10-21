package com.moonbench.bifrost

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.moonbench.bifrost.tools.CrashReporter

class BifrostApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        runCatching { cancelRetiredSchedule(this) }
    }

    companion object {
        /** Match earlier builds' alarm identity without retaining a receiver or scheduling new work. */
        internal fun cancelRetiredSchedule(context: Context) {
            val intent = Intent("com.moonbench.bifrost.action.SCHEDULE_TICK")
                .setClassName(context.packageName, "com.moonbench.bifrost.receivers.ScheduleReceiver")
            val pending = PendingIntent.getBroadcast(context, 8421, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
            context.getSystemService(AlarmManager::class.java).cancel(pending)
            pending.cancel()
        }
    }
}
