package com.ridescore.app.calculator

import com.ridescore.app.TestFixtures.offer
import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.settings.RideScoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The long-trip trap, from a real shift: a 30 km drop for ₹280 scored green,
 * and then there was no order back.
 */
class EmptyReturnTest {

    private val calculator = FareCalculator()

    private val driverRules = RideScoreSettings.DEFAULT.copy(
        acceptNetPerHour = 150.0,
        maybeNetPerHour = 130.0,
        minNetPerKm = 5.0,
    )

    /** 30 km drop, ₹280, an hour of riding, 2 km to the pickup. */
    private val longTrip = offer(totalFare = 280.0, pickupKm = 2.0, tripKm = 30.0, tripMin = 60.0)

    @Test
    fun `a long trip looks good on the paid leg alone`() {
        val a = calculator.analyse(longTrip, driverRules)

        assertEquals(32.0, a.totalDistanceKm, 0.001)
        assertEquals(102.40, a.fuelCost, 0.01)
        assertEquals(177.60, a.netEarning, 0.01)
        // 60 min of trip plus 8 min to the pickup: a green ₹157/hour.
        assertEquals(68.0, a.totalTimeMinutes, 0.01)
        assertEquals(156.71, a.netPerHour, 0.01)
        assertEquals(Decision.ACCEPT, a.decision)
        assertFalse(a.includesEmptyReturn)
    }

    @Test
    fun `the same trip is a reject once the ride back is counted`() {
        val a = calculator.analyse(longTrip, driverRules.copy(emptyReturnEnabled = true))

        // 30 km back at the speed of the trip out is another hour and another
        // 30 km of fuel, and none of it is paid.
        assertEquals(30.0, a.returnDistanceKm, 0.001)
        assertEquals(60.0, a.returnTimeMinutes, 0.01)
        assertEquals(62.0, a.totalDistanceKm, 0.001)
        assertEquals(198.40, a.fuelCost, 0.01)
        assertEquals(81.60, a.netEarning, 0.01)
        assertEquals(128.0, a.totalTimeMinutes, 0.01)
        // ₹157/hour becomes ₹38/hour once the ride home is counted.
        assertEquals(38.25, a.netPerHour, 0.01)
        assertEquals(Decision.REJECT, a.decision)
        assertTrue(a.includesEmptyReturn)
    }

    @Test
    fun `short trips are untouched by it`() {
        val short = offer(totalFare = 60.0, pickupKm = 1.8, tripKm = 5.9, tripMin = 12.0)
        val withReturn = calculator.analyse(short, driverRules.copy(emptyReturnEnabled = true))
        val without = calculator.analyse(short, driverRules)

        assertEquals(0.0, withReturn.returnDistanceKm, 0.001)
        assertEquals(without.netPerHour, withReturn.netPerHour, 0.001)
    }

    @Test
    fun `the threshold and the fraction are configurable`() {
        val halfWayBack = calculator.analyse(
            longTrip,
            driverRules.copy(emptyReturnEnabled = true, emptyReturnFraction = 0.5),
        )
        assertEquals(15.0, halfWayBack.returnDistanceKm, 0.001)

        val onlyVeryLong = calculator.analyse(
            longTrip,
            driverRules.copy(emptyReturnEnabled = true, emptyReturnFromKm = 40.0),
        )
        assertEquals(0.0, onlyVeryLong.returnDistanceKm, 0.001)
    }

    @Test
    fun `it is off unless the driver turns it on`() {
        assertFalse(RideScoreSettings.DEFAULT.emptyReturnEnabled)
    }
}
