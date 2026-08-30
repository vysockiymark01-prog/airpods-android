package io.github.vysockiymark01_prog.airpods

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.vysockiymark01_prog.airpods.audio.EqualizerPreferences
import io.github.vysockiymark01_prog.airpods.audio.SystemEqualizerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings shade toggle for the global equalizer (see [SystemEqualizerController]) — lets
 * you flip "iPhone-like" audio boost on/off without opening the app. Reads/writes the same
 * [EqualizerPreferences] DataStore the Activity's equalizer screen and the foreground service
 * both use, so all three entry points always agree on the current state.
 */
class EqualizerTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshTile() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val current = EqualizerPreferences.flow(applicationContext).first()
            val toggled = current.copy(enabled = !current.enabled)
            EqualizerPreferences.save(applicationContext, toggled)
            // Apply immediately in this process too, in case the foreground service isn't
            // running yet (e.g. Bluetooth was off) — otherwise the tile would say "on" with no
            // effect actually applied until the service next starts.
            SystemEqualizerController.ensureInitialized()
            SystemEqualizerController.apply(toggled)
            refreshTile()
        }
    }

    private suspend fun refreshTile() {
        val tile = qsTile ?: return
        val state = EqualizerPreferences.flow(applicationContext).first()
        tile.state = if (state.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.equalizer_tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification_earbud)
        tile.updateTile()
    }
}
