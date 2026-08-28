package com.ridescore.app.calculator

import com.ridescore.app.TestFixtures.offer
import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.settings.RideScoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The worked example from the brief, verified number by number:
 *
 * ₹45 + ₹15 = ₹60, 1.8 + 5.9 = 7.7 km, 7 + 12 = 19 min, fuel ₹24.64,
 * net ₹35.36, ₹111.66 net/hour -> REJECT.
 */
class FareCalculatorTest {

    private val calculator = FareCalculator()
    private val settings = RideScoreSettings.DEFAULT

    @Test
    fun `fuel cost per km is petrol price divided by mileage`() {
        assertEquals(3.20, settings.fuelCostPerKm, 0.001)
        assertEquals(
            4.0,
            settings.copy(petrolPricePerLitre = 120.0, mileageKmPerLitre = 30.0).fuelCostPerKm,
            0.001,
        )
    }

    @Test
    fun `total distance adds pickup and trip legs`() {
        val a = calculator.analyse(offer(), settings)
        assertEquals(7.7, a.totalDistanceKm, 0.0001)
    }

    @Test
    fun `total time is estimated pickup time plus trip time`() {
        val a = calculator.analyse(offer(), settings)
        // 1.8 km at 17 km/h is 6.35 min, shown as a whole 7 minutes.
        assertEquals(19.0, a.totalTimeMinutes, 0.0001)
        assertTrue(a.pickupTimeEstimated)
    }

    @Test
    fun `fuel cost for 7 point 7 km at 37 point 5 kmpl and 120 rupees`() {
        val a = calculator.analyse(offer(), settings)
        assertEquals(24.64, a.fuelCost, 0.001)
    }

    @Test
    fun `net earning is gross minus fuel when no optional costs are on`() {
        val a = calculator.analyse(offer(), settings)
        assertEquals(60.0, a.grossEarning, 0.001)
        assertEquals(35.36, a.netEarning, 0.001)
        assertEquals(0.0, a.maintenanceCost, 0.0001)
        assertEquals(0.0, a.platformFee, 0.0001)
    }

    @Test
    fun `net per hour and net per km match the brief`() {
        val a = calculator.analyse(offer(), settings)
        assertEquals(111.66, a.netPerHour, 0.01)
        assertEquals(4.59, a.netPerKm, 0.01)
        assertEquals(Decision.REJECT, a.decision)
    }

    @Test
    fun `gross rates are reported separately from net rates`() {
        val a = calculator.analyse(offer(), settings)
        assertEquals(189.47, a.grossPerHour, 0.01) // 60 / (19/60)
        assertEquals(7.79, a.grossPerKm, 0.01) // 60 / 7.7
        assertTrue(a.grossPerHour > a.netPerHour)
    }

    @Test
    fun `a pickup time printed on screen is used instead of the estimate`() {
        val a = calculator.analyse(offer(pickupMin = 4.0), settings)
        assertEquals(16.0, a.totalTimeMinutes, 0.0001)
        assertFalse(a.pickupTimeEstimated)
    }

    @Test
    fun `pickup speed setting drives the estimate`() {
        assertEquals(7.0, FareCalculator.estimatePickupMinutes(1.8, 17.0), 0.0001)
        assertEquals(5.0, FareCalculator.estimatePickupMinutes(1.4, 17.0), 0.0001)
        assertEquals(11.0, FareCalculator.estimatePickupMinutes(1.8, 10.0), 0.0001)
        assertEquals(0.0, FareCalculator.estimatePickupMinutes(0.0, 17.0), 0.0001)
    }

    @Test
    fun `excluding the pickup leg drops it from distance, time and fuel`() {
        val a = calculator.analyse(
            offer(),
            settings.copy(includePickupDistance = false, includePickupTime = false),
        )
        assertEquals(5.9, a.totalDistanceKm, 0.0001)
        assertEquals(12.0, a.totalTimeMinutes, 0.0001)
        assertEquals(18.88, a.fuelCost, 0.001)
    }

    @Test
    fun `maintenance is only subtracted when the driver turns it on`() {
        val off = calculator.analyse(offer(), settings)
        val on = calculator.analyse(
            offer(),
            settings.copy(maintenanceEnabled = true, maintenancePerKm = 1.5),
        )
        assertEquals(0.0, off.maintenanceCost, 0.0001)
        assertEquals(11.55, on.maintenanceCost, 0.001) // 7.7 * 1.5
        assertEquals(23.81, on.netEarning, 0.01)
    }

    @Test
    fun `platform fee is only subtracted when the driver turns it on`() {
        val on = calculator.analyse(
            offer(),
            settings.copy(platformFeeEnabled = true, platformFeePercent = 10.0),
        )
        assertEquals(6.0, on.platformFee, 0.001)
        assertEquals(29.36, on.netEarning, 0.001)
    }

    @Test
    fun `a good offer clears both thresholds`() {
        val a = calculator.analyse(
            offer(totalFare = 180.0, pickupKm = 1.0, tripKm = 8.0, tripMin = 20.0),
            settings,
        )
        assertEquals(Decision.ACCEPT, a.decision)
        assertTrue(a.netPerHour >= 150.0)
        assertTrue(a.netPerKm >= 9.0)
    }

    @Test
    fun `missing values never become zero-filled recommendations`() {
        val noFare = calculator.analyse(offer(totalFare = null), settings)
        assertEquals(Decision.CHECK, noFare.decision)

        val noTime = calculator.analyse(offer(tripMin = null, pickupKm = null), settings)
        assertEquals(Decision.CHECK, noTime.decision)
    }
}
