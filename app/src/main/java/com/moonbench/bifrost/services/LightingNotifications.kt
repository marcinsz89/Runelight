package com.moonbench.bifrost.services

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.moonbench.bifrost.R
import com.moonbench.bifrost.ui.RunelightShortcuts

internal object LightingNotifications {
    fun build(context: Context, notice: LightingNotice): Notification {
        val builder = Notification.Builder(context, LEDService.CHANNEL_ID)
            .setContentTitle(context.getString(notice.title))
            .setContentText(context.getString(notice.detail))
            .setSmallIcon(R.drawable.ic_runelight)
            .setContentIntent(PendingIntent.getActivity(context, 0,
                RunelightShortcuts.controlsIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(if (notice.ongoing) Notification.CATEGORY_SERVICE else Notification.CATEGORY_ERROR)
            .setOngoing(notice.ongoing)
            .setAutoCancel(!notice.ongoing)
        if (notice.ongoing) {
            val stop = PendingIntent.getService(context, 1,
                Intent(context, LEDService::class.java).setAction(LEDService.ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            builder.addAction(Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_delete),
                context.getString(R.string.runelight_stop), stop).build())
        }
        return builder.build()
    }
}
