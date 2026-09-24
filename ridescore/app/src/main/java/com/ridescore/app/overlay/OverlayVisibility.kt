package com.ridescore.app.overlay

/**
 * When the advisory card should disappear.
 *
 * Written as plain rules with no Android types because getting this wrong is
 * invisible in a unit test but very visible on a bike: the first field build
 * hid the card on every status-bar update, and on every single frame that
 * happened to read badly, which made it flash and vanish.
 *
 * The principle: an offer screen is alive and repainting, so the absence of a
 * reading is not evidence that the offer is gone. Only a real app switch, or a
 * few seconds of silence, means the card should go.
 */
object OverlayVisibility {

    /**
     * A frame that produced no readable offer. Offer screens repaint constantly
     * while the countdown ticks, and a repaint can be caught half-drawn, so one
     * empty read is not a reason to take the card away.
     */
    const val EMPTY_GRACE_MS = 4_000L

    /** The driver moved to another app - but they may bounce straight back. */
    const val APP_SWITCH_GRACE_MS = 1_200L

    private val TRANSIENT_MARKERS = listOf(
        "inputmethod", "keyboard", "honeyboard", "swiftkey", "gboard",
    )

    /**
     * True for windows that appear on top of the driver app without replacing
     * it: the status bar and notification shade, the keyboard, gesture hints.
     *
     * These fire window-state events constantly. Treating them as "the driver
     * left Rapido" is what made the card vanish a moment after appearing.
     */
    fun isTransientSystemPackage(packageName: String): Boolean {
        if (packageName.isEmpty()) return true
        if (packageName == "android") return true
        if (packageName.startsWith("com.android.systemui")) return true
        val lower = packageName.lowercase()
        return TRANSIENT_MARKERS.any { lower.contains(it) }
    }
}
