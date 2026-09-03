package com.ridescore.app.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card used to vanish a moment after appearing, because the status bar
 * counts as a window change and every empty frame counted as "offer gone".
 */
class OverlayVisibilityTest {

    @Test
    fun `system windows on top of the driver app are transient`() {
        assertTrue(OverlayVisibility.isTransientSystemPackage("com.android.systemui"))
        assertTrue(OverlayVisibility.isTransientSystemPackage("android"))
        assertTrue(OverlayVisibility.isTransientSystemPackage("com.google.android.inputmethod.latin"))
        assertTrue(OverlayVisibility.isTransientSystemPackage("com.samsung.android.honeyboard"))
        assertTrue(OverlayVisibility.isTransientSystemPackage("com.touchtype.swiftkey"))
        assertTrue(OverlayVisibility.isTransientSystemPackage(""))
    }

    @Test
    fun `real apps are not transient`() {
        assertFalse(OverlayVisibility.isTransientSystemPackage("com.rapido.rider"))
        assertFalse(OverlayVisibility.isTransientSystemPackage("com.ubercab.driver"))
        assertFalse(OverlayVisibility.isTransientSystemPackage("com.whatsapp"))
        assertFalse(OverlayVisibility.isTransientSystemPackage("com.google.android.apps.maps"))
    }

    @Test
    fun `an empty frame is given longer than an app switch`() {
        assertTrue(OverlayVisibility.EMPTY_GRACE_MS > OverlayVisibility.APP_SWITCH_GRACE_MS)
        assertTrue(OverlayVisibility.EMPTY_GRACE_MS >= 3_000L)
    }
}
