package com.moonbench.bifrost.services

import android.content.Context

/** Durable user intent, separate from UI settings and all live resources/grants. */
class LightingIntentStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("runelight_runtime", Context.MODE_PRIVATE)
    val wantedRunning: Boolean get() = prefs.getBoolean("wantedRunning", false)

    fun setWantedRunning(wanted: Boolean): Boolean =
        wantedRunning == wanted || prefs.edit().putBoolean("wantedRunning", wanted).commit()
}
