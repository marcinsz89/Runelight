package com.moonbench.bifrost.ui

import android.content.Context
import android.content.Intent
import com.moonbench.bifrost.RunelightActivity

internal object RunelightShortcuts {
    /** Opening controls is never a lighting command and never chooses a display. */
    fun controlsIntent(context: Context) = Intent(context, RunelightActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
