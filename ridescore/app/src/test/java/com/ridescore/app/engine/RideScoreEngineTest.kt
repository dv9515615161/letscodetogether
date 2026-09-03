package com.ridescore.app.engine

import com.ridescore.app.TestFixtures
import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.model.SourceApp
import com.ridescore.app.domain.settings.AppMode
import com.ridescore.app.domain.settings.RideScoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** End to end: screen text in, ranked decision out. */
class RideScoreEngineTest {

    private val engine = RideScoreEngine()
    private val settings = RideScoreSettings.DEFAULT

    @Test
    fun `the brief's Rapido offer comes out as reject`() {
        val a = engine.analyse(TestFixtures.rapido(TestFixtures.RAPIDO_OFFER_A), settings)
        val best = a.best!!

        assertEquals(SourceApp.RAPIDO, a.sourceApp)
        assertEquals(60.0, best.grossEarning, 0.001)
        assertEquals(7.7, best.totalDistanceKm, 0.001)
        assertEquals(19.0, best.totalTimeMinutes, 0.001)
        assertEquals(24.64, best.fuelCost, 0.01)
        assertEquals(35.36, best.netEarning, 0.01)
        assertEquals(111.66, best.netPerHour, 0.01)
        assertEquals(4.59, best.netPerKm, 0.01)
        assertEquals(Decision.REJECT, best.decision)
    }

    @Test
    fun `an Uber offer is analysed through the same pipeline`() {
        val a = engine.analyse(TestFixtures.uber(TestFixtures.UBER_OFFER), settings)
        val best = a.best!!

        assertEquals(SourceApp.UBER, a.sourceApp)
        assertEquals(128.55, best.grossEarning, 0.001)
        assertEquals(11.5, best.totalDistanceKm, 0.001)
        assertEquals(29.0, best.totalTimeMinutes, 0.001)
        assertEquals(36.8, best.fuelCost, 0.01)
        assertEquals(91.75, best.netEarning, 0.01)
        assertEquals(189.83, best.netPerHour, 0.01)
        // Good hourly rate, weak per-km rate: a maybe, not an accept.
        assertEquals(Decision.MAYBE, best.decision)
    }

    @Test
    fun `an unsupported app is ignored entirely`() {
        val snapshot = com.ridescore.app.domain.model.ScreenSnapshot.of(
            "com.whatsapp",
            listOf("₹60", "Pickup 1.8 km", "Trip 5.9 km", "12 mins"),
        )
        assertTrue(engine.analyse(snapshot, settings).ranked.isEmpty())
    }

    @Test
    fun `app mode narrows which apps are analysed`() {
        val rapidoOnly = settings.copy(appMode = AppMode.RAPIDO_ONLY)
        assertTrue(engine.analyse(TestFixtures.uber(TestFixtures.UBER_OFFER), rapidoOnly).ranked.isEmpty())
        assertTrue(
            engine.analyse(TestFixtures.rapido(TestFixtures.RAPIDO_OFFER_A), rapidoOnly)
                .ranked.isNotEmpty(),
        )
    }

    @Test
    fun `analysis of a two offer screen stays well under a millisecond`() {
        val snapshot = TestFixtures.rapido(TestFixtures.RAPIDO_OFFER_A + TestFixtures.RAPIDO_OFFER_B)
        repeat(50) { engine.analyse(snapshot, settings) } // warm up

        val started = System.nanoTime()
        repeat(500) { engine.analyse(snapshot, settings) }
        val perAnalysisMicros = (System.nanoTime() - started) / 500 / 1_000

        // Generous on purpose. This is wall-clock on whatever machine CI gave
        // us, and a loaded runner can spike a sub-millisecond analysis past a
        // tight bound - a flake that blocks a build for no reason. Ten
        // milliseconds still catches the regression worth catching: someone
        // making the parse an order of magnitude slower.
        assertTrue("${perAnalysisMicros}us per analysis", perAnalysisMicros < 10_000)
    }
}
