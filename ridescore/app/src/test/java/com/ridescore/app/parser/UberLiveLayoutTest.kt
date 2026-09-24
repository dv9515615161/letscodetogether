package com.ridescore.app.parser

import com.ridescore.app.TestFixtures
import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.settings.AppMode
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.engine.RideScoreEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A real Uber Driver offer, reported from Hyderabad.
 *
 * ```
 * Bike Saver
 * ₹169.44
 * Cash payment   ★ 5.00
 * 3 min (0.1 km)     Allwyn Colony, Kukatpally, Hyderabad, 500072
 * 38 mins (17.2 km)  Castrol Auto Service - Shradha Motors, Alkapur Twp, 500089
 * ```
 *
 * Unlike the layout the Uber parser was first written against, neither leg is
 * labelled "away" or "trip" - the two rows are distinguished only by order.
 */
class UberLiveLayoutTest {

    private val parser = UberParser()
    private val engine = RideScoreEngine()

    private val screen = listOf(
        "Bike Saver",
        "₹169.44",
        "Cash payment",
        "★ 5.00",
        "3 min (0.1 km)",
        "Allwyn Colony, Kukatpally, Hyderabad, 500072",
        "38 mins (17.2 km)",
        "Castrol Auto Service - Shradha Motors, Alkapur Twp, Vinayaka Nagar Colony, Hyderabad, 500089",
        "Confirm",
    )

    @Test
    fun `reads both legs from their order alone`() {
        val offer = parser.parse(TestFixtures.uber(screen)).single()

        assertEquals(169.44, offer.totalFare!!, 0.001)
        assertEquals(0.1, offer.pickupDistanceKm!!, 0.001)
        assertEquals(3.0, offer.pickupTimeMinutes!!, 0.001)
        assertEquals(17.2, offer.tripDistanceKm!!, 0.001)
        assertEquals(38.0, offer.tripTimeMinutes!!, 0.001)
    }

    @Test
    fun `a five point zero rating is not mistaken for a fare`() {
        val offer = parser.parse(TestFixtures.uber(screen)).single()
        assertEquals(169.44, offer.totalFare!!, 0.001)
    }

    @Test
    fun `scores it end to end`() {
        val settings = RideScoreSettings.DEFAULT.copy(
            acceptNetPerHour = 160.0,
            maybeNetPerHour = 130.0,
            minNetPerKm = 5.0,
        )
        val best = engine.analyse(TestFixtures.uber(screen), settings).best!!

        assertEquals(17.3, best.totalDistanceKm, 0.001)
        assertEquals(41.0, best.totalTimeMinutes, 0.001)
        assertEquals(55.36, best.fuelCost, 0.01)
        assertEquals(114.08, best.netEarning, 0.01)
        assertEquals(166.95, best.netPerHour, 0.01)
        assertEquals(Decision.ACCEPT, best.decision)
    }

    @Test
    fun `watching Rapido only means Uber is never even read`() {
        val rapidoOnly = RideScoreSettings.DEFAULT.copy(appMode = AppMode.RAPIDO_ONLY)
        assertTrue(engine.analyse(TestFixtures.uber(screen), rapidoOnly).ranked.isEmpty())
    }
}
