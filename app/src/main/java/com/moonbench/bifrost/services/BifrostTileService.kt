package com.moonbench.bifrost.services

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat
import com.moonbench.bifrost.R
import com.moonbench.bifrost.ui.RunelightShortcuts

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
        if (isLocked) unlockAndRun { openApp() } else openApp()
    }

    private fun openApp() {
        TileServiceCompat.startActivityAndCollapse(this, PendingIntentActivityWrapper(
            this, 0, RunelightShortcuts.controlsIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT, false))
    }

    private fun renderState() {
        val tile = qsTile ?: return
        val runtime = LEDService.state.value ?: LightingState()
        val running = runtime.phase == LightingPhase.STARTING || runtime.phase == LightingPhase.RUNNING
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.subtitle = getString(when (runtime.phase) {
            LightingPhase.RUNNING -> LightingNotice.effectLabel(runtime.effect)
            LightingPhase.STARTING -> R.string.runelight_starting
            else -> R.string.runelight_off
        })
        tile.contentDescription = getString(R.string.runelight_shortcut_description, tile.subtitle)
        tile.updateTile()
    }
}
