package com.scrollingo.app

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile that pauses/resumes Scrollingo from the notification shade.
 *
 * The tile reflects the paused flag in [Prefs]:
 *  - ACTIVE  (highlighted/colored) → Scrollingo is running and will quiz you,
 *  - INACTIVE (gray/dimmed)        → Scrollingo is paused, no quizzes fire.
 *
 * The actual gating happens in [ScrollDetectorService.handleScroll], which bails
 * out early while [Prefs.isPaused] is true.
 */
class ScrollingoTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Toggle paused state, then repaint the tile to match.
        Prefs.setPaused(this, !Prefs.isPaused(this))
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val paused = Prefs.isPaused(this)

        tile.state = if (paused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_cat)
        tile.contentDescription =
            if (paused) "Scrollingo paused" else "Scrollingo active"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (paused) "Paused" else "Active"
        }
        tile.updateTile()
    }
}
