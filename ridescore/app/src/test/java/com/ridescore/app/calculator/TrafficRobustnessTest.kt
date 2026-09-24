package com.ridescore.app.calculator

import com.ridescore.app.TestFixtures.offer
import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.model.DecisionReason
import com.ridescore.app.domain.settings.RideScoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ACCEPT must survive the traffic, not just the average.
 *
 * A driver asked the right question: if the app assumes 24 km/h and the peak
 * hour is slower, the ₹150/hr it promised is not there. His own log says he is
 * right, and by more than anyone would guess. Across 172 offers that printed
 * both a distance and a duration:
 *
 * | Hour | Median speed |
 * |---|---|
 * | 07:00 | 30.2 km/h |
 * | 08:00 | 20.6 km/h |
 * | 09:00 | **14.2 km/h** |
 *
 * At 09:00 - his busiest hour - the road runs at *half* the average. A 12 km
 * trip takes 51 minutes, not 30, so an offer sold at ₹150 an hour pays ₹88.
 *
 * The fix is not a better guess. It is to stop treating a guess as a promise:
 * when the duration was estimated, ACCEPT is shown only if the offer still
 * clears the bar at slow-traffic speed. Anything that clears only on a good
 * run is a MAYBE - true, and useful, because it depends on the road.
 *
 * A duration the app actually printed is never stress-tested. It is a fact.
 */
class TrafficRobustnessTest {

    private val calculator = FareCalculator()
    private val settings = RideScoreSettings.DEFAULT

    @Test
    fun `an offer that only clears on a good run is a MAYBE`() {
        // ₹46 over 3 km: 9 minutes if the road runs, 14 if it does not.
        val a = calculator.analyse(
            offer(totalFare = 46.0, pickupKm = 0.5, tripKm = 3.0, tripMin = null),
            settings,
        )

        assertTrue("clears at the usual speed", a.netPerHour >= settings.acceptNetPerHour)
        assertTrue("but not in traffic", a.netPerHourInTraffic < settings.acceptNetPerHour)
        // Nothing else is holding it back - ₹9.94/km clears the ₹9 floor.
        assertTrue(a.netPerKm >= settings.minNetPerKm)
        assertEquals(Decision.MAYBE, a.decision)
        assertTrue(a.reasons.contains(DecisionReason.FAILS_IN_TRAFFIC))
    }

    @Test
    fun `an offer good enough to survive bad traffic is still an ACCEPT`() {
        val a = calculator.analyse(
            offer(totalFare = 220.0, pickupKm = 0.5, tripKm = 6.0, tripMin = null),
            settings,
        )

        assertTrue(a.netPerHourInTraffic >= settings.acceptNetPerHour)
        assertEquals(Decision.ACCEPT, a.decision)
    }

    @Test
    fun `a printed duration is taken as read and never stress-tested`() {
        val a = calculator.analyse(
            offer(totalFare = 100.0, pickupKm = 1.0, tripKm = 6.0, tripMin = 14.0),
            settings,
        )

        assertEquals(false, a.tripTimeEstimated)
        // No penalty, no doubt: the app said 14 minutes, so 14 minutes it is.
        assertEquals(a.netPerHour, a.netPerHourInTraffic, 0.001)
        assertEquals(a.totalTimeMinutes, a.totalTimeMinutesInTraffic, 0.001)
        assertEquals(Decision.ACCEPT, a.decision)
    }

    @Test
    fun `the slow case costs time but never fuel`() {
        val a = calculator.analyse(
            offer(totalFare = 126.0, pickupKm = 0.2, tripKm = 12.6, tripMin = null),
            settings,
        )
        // Traffic makes the same kilometres take longer. It does not make them
        // longer, so the distance, the fuel and the ₹/km are untouched.
        assertTrue(a.totalTimeMinutesInTraffic > a.totalTimeMinutes)
        assertTrue(a.netPerHourInTraffic < a.netPerHour)
        assertEquals(12.8, a.totalDistanceKm, 0.001)
    }

    @Test
    fun `the driver can switch the stress test off`() {
        val offer = offer(totalFare = 46.0, pickupKm = 0.5, tripKm = 3.0, tripMin = null)
        val strict = calculator.analyse(offer, settings)
        val relaxed = calculator.analyse(
            offer,
            settings.copy(requireAcceptToSurviveTraffic = false),
        )

        assertEquals(Decision.MAYBE, strict.decision)
        assertEquals(Decision.ACCEPT, relaxed.decision)
        // Same money either way - only the recommendation changes.
        assertEquals(strict.netEarning, relaxed.netEarning, 0.001)
    }

    @Test
    fun `short trips are assumed slower than long ones, because they are`() {
        // Measured medians: 14.9 km/h under 2 km, 22.0 from 2 to 5, 32.6 over.
        assertEquals(14.9, settings.tripSpeedFor(1.5), 0.1)
        assertEquals(22.1, settings.tripSpeedFor(3.0), 0.1)
        assertEquals(31.7, settings.tripSpeedFor(9.0), 0.1)

        // A 2 km hop is not two-fifteenths of a 15 km run.
        val short = calculator.analyse(
            offer(totalFare = 40.0, pickupKm = 0.5, tripKm = 1.5, tripMin = null),
            settings,
        )
        assertEquals(7.0, short.tripTimeMinutesCounted, 0.001)
    }

    @Test
    fun `slow-traffic speed is the peak hour this driver actually rides in`() {
        // 09:00 median was 14.2 km/h. A long trip's slow speed lands there.
        assertEquals(19.0, settings.slowTripSpeedFor(9.0), 0.2)
        assertEquals(8.9, settings.slowTripSpeedFor(1.5), 0.2)
        assertEquals(0.6, RideScoreSettings.DEFAULT.slowTrafficFactor, 0.001)
    }
}
