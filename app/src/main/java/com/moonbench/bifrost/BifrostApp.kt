package com.moonbench.bifrost

import android.app.Application
import com.moonbench.bifrost.schedule.ScheduleAlarms
import com.moonbench.bifrost.tools.CrashReporter

class BifrostApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        runCatching {
            // Retire any alarm registered by the earlier internal builds. This
            // cancels only Runelight's schedule PendingIntent, not device alarms.
            ScheduleAlarms.cancel(this)
        }
    }
}
