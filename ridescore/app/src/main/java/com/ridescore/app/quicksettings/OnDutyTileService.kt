package com.ridescore.app.quicksettings

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ridescore.app.R
import com.ridescore.app.data.settings.SettingsCache
import com.ridescore.app.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A Quick Settings tile for the master switch.
 *
 * A driver is not always driving, and RideScore holds a standing permission to
 * read two apps' screens. Withdrawing that should not mean unlocking the phone,
 * finding the app and hunting through settings - especially at a signal, on a
 * bike, in the sun. From the notification shade it is one tap.
 *
 * Off means off: [com.ridescore.app.domain.settings.RideScoreSettings.onDuty]
 * gates [com.ridescore.app.domain.settings.RideScoreSettings.watches], which is
 * the single check the accessibility service makes before it asks Android for a
 * window's contents. Nothing is read and then discarded.
 */
class OnDutyTileService : TileService() {

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        render(SettingsCache.current.onDuty)
    }

    override fun onClick() {
        super.onClick()
        val turningOn = !SettingsCache.current.onDuty

        // Draw the new state at once. The write is quick, but the tile should
        // never look like it ignored the tap.
        render(turningOn)

        val repository = SettingsRepository(applicationContext)
        val active = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        active.launch {
            runCatching { repository.update { it.copy(onDuty = turningOn) } }
                .onFailure { render(!turningOn) }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    private fun render(onDuty: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (onDuty) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.contentDescription = getString(
            if (onDuty) R.string.tile_on_duty else R.string.tile_off_duty,
        )
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_ridescore)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                if (onDuty) R.string.tile_state_watching else R.string.tile_state_off,
            )
        }
        tile.updateTile()
    }
}
