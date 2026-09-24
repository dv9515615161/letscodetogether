package com.ridescore.app.overlay

import android.content.Context

/**
 * Remembers where the driver dragged the card.
 *
 * Kept out of the settings store so dragging the overlay does not churn the
 * settings flow (and the decision cache) on every finger move.
 */
class OverlayPositionStore(context: Context) {

    private val prefs = context.getSharedPreferences("ridescore_overlay", Context.MODE_PRIVATE)

    fun x(default: Int): Int = prefs.getInt(KEY_X, default)

    fun y(default: Int): Int = prefs.getInt(KEY_Y, default)

    fun save(x: Int, y: Int) {
        prefs.edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()
    }

    fun reset() {
        prefs.edit().remove(KEY_X).remove(KEY_Y).apply()
    }

    private companion object {
        const val KEY_X = "overlay_x"
        const val KEY_Y = "overlay_y"
    }
}
