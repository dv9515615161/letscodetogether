package com.ridescore.app.settings

import com.ridescore.app.TestFixtures.RAPIDO_OFFER_A
import com.ridescore.app.TestFixtures.UBER_OFFER
import com.ridescore.app.TestFixtures.rapido
import com.ridescore.app.TestFixtures.uber
import com.ridescore.app.domain.model.SourceApp
import com.ridescore.app.domain.settings.AppMode
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.engine.RideScoreEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The master switch.
 *
 * A driver is not always driving, and RideScore holds a standing permission to
 * read two apps' screens. "Off" therefore has to mean off - not read-then-
 * discard, and not "the card stops appearing while it keeps reading".
 *
 * [RideScoreSettings.watches] is the one gate every read passes through: the
 * accessibility service consults it before asking Android for a window's
 * contents, and the engine consults it again before parsing. Folding the
 * switch into that function is what makes the guarantee hold everywhere at
 * once, rather than in the places someone remembered to check.
 */
class OnDutyTest {

    private val engine = RideScoreEngine()

    @Test
    fun `off duty means no app is watched at all`() {
        val off = RideScoreSettings.DEFAULT.copy(onDuty = false)
        assertFalse(off.watches(SourceApp.RAPIDO))
        assertFalse(off.watches(SourceApp.UBER))
        assertFalse(off.watches(SourceApp.UNKNOWN))
    }

    @Test
    fun `off duty overrides the app mode, whatever it is set to`() {
        for (mode in AppMode.entries) {
            val off = RideScoreSettings.DEFAULT.copy(onDuty = false, appMode = mode)
            assertFalse("$mode still watched Rapido", off.watches(SourceApp.RAPIDO))
            assertFalse("$mode still watched Uber", off.watches(SourceApp.UBER))
        }
    }

    @Test
    fun `a real offer screen produces nothing while off duty`() {
        val off = RideScoreSettings.DEFAULT.copy(onDuty = false)
        assertTrue(engine.analyse(rapido(RAPIDO_OFFER_A), off).ranked.isEmpty())
        assertTrue(engine.analyse(uber(UBER_OFFER), off).ranked.isEmpty())
    }

    @Test
    fun `turning it back on restores exactly what was there before`() {
        val on = RideScoreSettings.DEFAULT
        val off = on.copy(onDuty = false)

        assertTrue(engine.analyse(rapido(RAPIDO_OFFER_A), off).ranked.isEmpty())
        val back = engine.analyse(rapido(RAPIDO_OFFER_A), off.copy(onDuty = true))
        val never = engine.analyse(rapido(RAPIDO_OFFER_A), on)

        assertEquals(never.ranked.size, back.ranked.size)
        assertEquals(never.ranked.first().decision, back.ranked.first().decision)
    }

    @Test
    fun `on duty by default, so a fresh install is not silently doing nothing`() {
        assertTrue(RideScoreSettings.DEFAULT.onDuty)
        assertTrue(RideScoreSettings.DEFAULT.watches(SourceApp.RAPIDO))
    }

    @Test
    fun `the app mode still applies while on duty`() {
        val rapidoOnly = RideScoreSettings.DEFAULT.copy(appMode = AppMode.RAPIDO_ONLY)
        assertTrue(rapidoOnly.watches(SourceApp.RAPIDO))
        assertFalse(rapidoOnly.watches(SourceApp.UBER))
    }
}
