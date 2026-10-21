package com.moonbench.bifrost.services

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.moonbench.bifrost.RunelightActivity

class BifrostTileService : TileService() {

    companion object {
        fun refreshFrom(context: Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, BifrostTileService::class.java)
                )
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        renderState()
    }

    override fun onClick() {
        super.onClick()
        // A shortcut opens the current controls; it never starts a legacy preset
        // or requests capture from a background component.
        openApp()
    }

    private fun openApp() {
        val launch = Intent(this, RunelightActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launch)
        }
    }

    private fun renderState() {
        val tile = qsTile ?: return
        val running = LEDService.isRunning
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
