package com.ridescore.app.parser

import com.ridescore.app.TestFixtures
import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.engine.RideScoreEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rapido regularly stacks two or three offers on one screen. */
class MultipleOffersTest {

    private val parser = RapidoParser()
    private val engine = RideScoreEngine()
    private val settings = RideScoreSettings.DEFAULT

    private val twoOffers = TestFixtures.RAPIDO_OFFER_A + TestFixtures.RAPIDO_OFFER_B

    @Test
    fun `splits a flat list of lines into two offers`() {
        val offers = parser.parse(TestFixtures.rapido(twoOffers))
        assertEquals(2, offers.size)
        assertEquals(60.0, offers[0].totalFare!!, 0.001)
        assertEquals(66.0, offers[1].totalFare!!, 0.001)
        assertEquals(5.9, offers[0].tripDistanceKm!!, 0.001)
        assertEquals(6.5, offers[1].tripDistanceKm!!, 0.001)
    }

    @Test
    fun `uses the cards the accessibility tree already separated`() {
        val offers = parser.parse(
            TestFixtures.rapido(
                lines = twoOffers,
                blocks = listOf(TestFixtures.RAPIDO_OFFER_A, TestFixtures.RAPIDO_OFFER_B),
            ),
        )
        assertEquals(2, offers.size)
    }

    @Test
    fun `ranks both offers and reports that none is worth taking`() {
        val analysis = engine.analyse(TestFixtures.rapido(twoOffers), settings)

        assertEquals(2, analysis.ranked.size)
        assertTrue(analysis.hasMultipleOffers)

        val best = analysis.best!!
        assertEquals(60.0, best.grossEarning, 0.001)
        assertEquals(7.7, best.totalDistanceKm, 0.001)
        assertEquals(19.0, best.totalTimeMinutes, 0.001)
        assertEquals(111.66, best.netPerHour, 0.01)

        val second = analysis.ranked[1]
        assertEquals(66.0, second.grossEarning, 0.001)
        assertEquals(7.9, second.totalDistanceKm, 0.001)
        assertEquals(22.0, second.totalTimeMinutes, 0.001)

        // Best is best by net rupees per hour.
        assertTrue(best.netPerHour >= second.netPerHour)
        assertTrue(analysis.noGoodOrder)
    }

    @Test
    fun `picks the better of a good and a bad offer`() {
        val good = listOf("Bike", "₹210", "Pickup 1.0 km", "Trip 9.0 km", "Trip time 22 mins")
        val analysis = engine.analyse(
            TestFixtures.rapido(TestFixtures.RAPIDO_OFFER_A + good),
            settings,
        )
        assertEquals(2, analysis.ranked.size)
        assertEquals(210.0, analysis.best!!.grossEarning, 0.001)
        assertEquals(Decision.ACCEPT, analysis.best!!.decision)
        assertEquals(Decision.REJECT, analysis.ranked[1].decision)
        assertFalse(analysis.noGoodOrder)
    }

    @Test
    fun `handles three offers on one screen`() {
        val third = listOf("Bike", "₹80", "Pickup 2.0 km", "Trip 4.0 km", "Trip time 10 mins")
        val offers = parser.parse(TestFixtures.rapido(twoOffers + third))
        assertEquals(3, offers.size)
    }
}
