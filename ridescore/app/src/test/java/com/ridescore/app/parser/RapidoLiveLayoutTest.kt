package com.ridescore.app.parser

import com.ridescore.app.TestFixtures
import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.engine.RideScoreEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layout from a real Rapido Captain offer, reported from a shift in
 * Hyderabad.
 *
 * ```
 * Bike
 * ₹45 + ₹18
 * 3.5 km      Kukatpally - 24-230, Kukatpally House Phase 1, Balanagar, 500072
 * 5.9 km (13 mins)  Gajularamaram - Sri Balaji Layout, Hyderabad, Telangana
 * Accept
 * See 2 more orders
 * ```
 *
 * Two things this layout does that the earlier fixtures did not: there are no
 * "Pickup" or "Drop" words anywhere - the legs are marked with a dot and an
 * arrow icon - and the trip time is inside the drop line's brackets.
 */
class RapidoLiveLayoutTest {

    private val parser = RapidoParser()
    private val engine = RideScoreEngine()
    private val settings = RideScoreSettings.DEFAULT

    private val screen = listOf(
        "Bike",
        "₹45 + ₹18",
        "3.5 km",
        "Kukatpally - 24-230, Kukatpally House Phase 1, Balanagar, 500072",
        "5.9 km (13 mins)",
        "Gajularamaram - Sri Balaji Layout, Gajularamaram, Hyderabad, Telangana 500117, India",
        "Accept",
        "See 2 more orders",
    )

    @Test
    fun `reads an unlabelled offer by position`() {
        val offer = parser.parse(TestFixtures.rapido(screen)).single()

        assertEquals(45.0, offer.baseFare!!, 0.001)
        assertEquals(18.0, offer.bonusFare!!, 0.001)
        assertEquals(63.0, offer.totalFare!!, 0.001)
        assertEquals(3.5, offer.pickupDistanceKm!!, 0.001)
        assertEquals(5.9, offer.tripDistanceKm!!, 0.001)
        assertEquals(13.0, offer.tripTimeMinutes!!, 0.001)
    }

    @Test
    fun `scores it end to end`() {
        val analysis = engine.analyse(TestFixtures.rapido(screen), settings)
        val best = analysis.best!!

        assertEquals(63.0, best.grossEarning, 0.001)
        assertEquals(9.4, best.totalDistanceKm, 0.001) // 3.5 + 5.9
        // 3.5 km at 17 km/h is 12.35 min, counted as 13, plus the 13 min trip.
        assertEquals(26.0, best.totalTimeMinutes, 0.001)
        assertEquals(30.08, best.fuelCost, 0.01) // 9.4 * 3.20
        assertEquals(32.92, best.netEarning, 0.01)
        assertEquals(75.97, best.netPerHour, 0.01)
        assertEquals(Decision.REJECT, best.decision)
    }

    @Test
    fun `finds the fare when it sits outside the offer card`() {
        // The fare lives in its own view, away from the journey lines - the
        // case that produced "Could not read fare" on a real screen.
        val snapshot = TestFixtures.rapido(
            lines = screen,
            blocks = listOf(
                listOf("3.5 km", "5.9 km (13 mins)", "Accept"),
            ),
        )
        val offer = parser.parse(snapshot).single()

        assertEquals(63.0, offer.totalFare!!, 0.001)
        assertTrue(offer.notes.any { it.contains("elsewhere") })
        // Trusted, but less than a fare found inside the card.
        assertTrue(offer.confidence < 0.95f)
    }

    @Test
    fun `does not mistake a wallet balance for the fare`() {
        val snapshot = TestFixtures.rapido(
            lines = listOf(
                "3.5 km",
                "5.9 km (13 mins)",
                "Low Balance- Orders will be blocked",
                "Wallet balance is low",
                "₹112",
                "Pay Now",
            ),
            blocks = listOf(listOf("3.5 km", "5.9 km (13 mins)")),
        )
        val offer = parser.parse(snapshot).single()

        assertNotNull(offer.tripDistanceKm)
        // A bare amount on an unrelated banner is not a fare.
        assertEquals(null, offer.totalFare)
    }

    @Test
    fun `a missing fare says exactly what was missing`() {
        val analysis = engine.analyse(
            TestFixtures.rapido(listOf("Bike", "3.5 km", "5.9 km (13 mins)")),
            settings,
        )
        assertEquals(Decision.CHECK, analysis.best!!.decision)
    }
}
