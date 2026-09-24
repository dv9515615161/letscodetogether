package com.ridescore.app.calculator

import com.ridescore.app.TestFixtures.offer
import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.settings.RideScoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bug a real ride log caught: **a missing trip time counted as no time**.
 *
 * Rapido frequently shows a fare and a distance and no duration at all. The
 * calculator read that as zero trip minutes, so the whole fare was divided by
 * the pickup leg alone. In 1,894 logged offers, 393 were scored this way, and
 * they claimed a median of ₹386 an hour. The worst of them:
 *
 * | Offer | Time used | Claimed | Shown as |
 * |---|---|---|---|
 * | ₹126, 12.6 km | 1 min | ₹4,365/hr | MAYBE |
 * | ₹113, 10.1 km | 1 min | ₹4,086/hr | MAYBE |
 * | ₹106, 7.5 km | 2 min | ₹2,402/hr | MAYBE |
 *
 * A 12.6 km ride does not take one minute. Estimating the leg from its
 * distance - the same bargain already struck for the pickup leg, marked with
 * the same tilde - drops those to ₹132, ₹116 and ₹155 an hour. Replayed over
 * the whole log it downgrades 157 of the 393, 148 of them from MAYBE to
 * REJECT.
 *
 * Every case here uses the settings the log itself was recorded under: fuel at
 * the defaults and a flat 10% platform deduction, which the log's own
 * `platform_fee` column fits to within half a paisa across 1,003 rows.
 */
class MissingTripTimeTest {

    private val calculator = FareCalculator()

    /** As the log was recorded: default fuel, flat 10% off every fare. */
    private val settings = RideScoreSettings.DEFAULT.copy(taxesAndFeesPercent = 10.0)

    @Test
    fun `a twelve km ride is not a one minute ride`() {
        val a = calculator.analyse(
            offer(totalFare = 126.0, pickupKm = 0.2, tripKm = 12.6, tripMin = null),
            settings,
        )

        assertTrue(a.tripTimeEstimated)
        // 12.6 km is a long trip: 24 x 1.32 = 31.7 km/h, so 24 minutes.
        assertEquals(24.0, a.tripTimeMinutesCounted, 0.001)
        assertEquals(25.0, a.totalTimeMinutes, 0.001)
        assertEquals(173.9, a.netPerHour, 0.5) // the log claimed ₹4,365
        // And 41 minutes if the road is slow, which is ₹106 an hour.
        assertEquals(41.0, a.totalTimeMinutesInTraffic, 0.001)
        assertEquals(106.0, a.netPerHourInTraffic, 0.5)
    }

    @Test
    fun `the fabricated rates collapse to what a bike can really earn`() {
        // Straight from the ride log: fare, trip km, pickup km. Scoring each
        // one twice - once with the trip stated as zero minutes, which is what
        // the old code did with a missing time, and once estimated.
        val logged = listOf(
            Triple(126.0, 12.6, 0.2),
            Triple(113.0, 10.1, 0.2),
            Triple(106.0, 7.5, 0.4),
            Triple(118.0, 11.7, 0.9),
            Triple(175.0, 14.7, 0.9),
        )
        for ((fare, km, pickup) in logged) {
            val zeroFilled = calculator.analyse(
                offer(totalFare = fare, pickupKm = pickup, tripKm = km, tripMin = 0.0),
                settings,
            )
            val estimated = calculator.analyse(
                offer(totalFare = fare, pickupKm = pickup, tripKm = km, tripMin = null),
                settings,
            )

            val label = "₹$fare over $km km"
            // Absurd by any measure: the median readable offer in that same
            // log nets ₹175 an hour.
            assertTrue("$label was not absurd to begin with", zeroFilled.netPerHour > 500.0)
            // The five fall by 6.7x to 25x. The gap is widest where the
            // fabrication was worst: the shorter the pickup leg the old code
            // divided by, the bigger the lie.
            assertTrue(
                "$label only fell to ₹${estimated.netPerHour.toInt()}/hr " +
                    "from ₹${zeroFilled.netPerHour.toInt()}/hr",
                estimated.netPerHour < zeroFilled.netPerHour / 6.0,
            )
            // And what is left is a rate a bike can actually earn. Not all of
            // them are bad: ₹106 over 7.5 km really is ₹200 an hour. The fix
            // is not a blanket downgrade, it is the truth instead of a
            // fabrication.
            assertTrue("$label reads ₹${estimated.netPerHour.toInt()}/hr", estimated.netPerHour < 250.0)
        }
    }

    @Test
    fun `not one of them is recommended any more`() {
        // Nine offers the log scored on a fabricated rate, four of them shown
        // as MAYBE at over ₹850 an hour. None is an ACCEPT now, and the two
        // worst are outright rejects.
        val logged = listOf(
            Triple(126.0, 12.6, 0.2), Triple(113.0, 10.1, 0.2),
            Triple(106.0, 7.5, 0.4), Triple(118.0, 11.7, 0.9),
            Triple(175.0, 14.7, 0.9), Triple(111.0, 12.0, 1.0),
        )
        for ((fare, km, pickup) in logged) {
            val a = calculator.analyse(
                offer(totalFare = fare, pickupKm = pickup, tripKm = km, tripMin = null),
                settings,
            )
            assertTrue(
                "₹$fare over $km km is still an ACCEPT at ₹${a.netPerHour.toInt()}/hr",
                a.decision != Decision.ACCEPT,
            )
        }

        for ((fare, km, pickup) in listOf(Triple(105.0, 12.0, 1.0), Triple(95.0, 12.0, 1.0))) {
            val a = calculator.analyse(
                offer(totalFare = fare, pickupKm = pickup, tripKm = km, tripMin = null),
                settings,
            )
            assertEquals("₹$fare at ₹${a.netPerHour.toInt()}/hr", Decision.REJECT, a.decision)
        }
    }

    @Test
    fun `a stated trip time is always preferred to an estimate`() {
        val a = calculator.analyse(
            offer(totalFare = 126.0, pickupKm = 0.2, tripKm = 12.6, tripMin = 20.0),
            settings,
        )
        assertFalse(a.tripTimeEstimated)
        assertEquals(20.0, a.tripTimeMinutesCounted, 0.001)
        // 1 min estimated pickup + 20 stated.
        assertEquals(21.0, a.totalTimeMinutes, 0.001)
    }

    @Test
    fun `the estimate is announced, not slipped in`() {
        val a = calculator.analyse(
            offer(totalFare = 90.0, pickupKm = 1.0, tripKm = 6.0, tripMin = null),
            settings,
        )
        assertTrue(a.notes.any { it.contains("Trip time estimated") })
        assertTrue(a.notes.any { it.contains("did not show it") })
    }

    @Test
    fun `no distance means no estimate and no guess at a rate`() {
        val a = calculator.analyse(
            offer(totalFare = 90.0, pickupKm = null, tripKm = null, tripMin = null),
            settings,
        )
        assertFalse(a.tripTimeEstimated)
        assertEquals(Decision.CHECK, a.decision)
    }

    @Test
    fun `a faster rider gets a shorter estimate and a better rate`() {
        val slow = calculator.analyse(
            offer(totalFare = 126.0, pickupKm = 0.2, tripKm = 12.6, tripMin = null),
            settings.copy(tripSpeedKmph = 15.0),
        )
        val fast = calculator.analyse(
            offer(totalFare = 126.0, pickupKm = 0.2, tripKm = 12.6, tripMin = null),
            settings.copy(tripSpeedKmph = 35.0),
        )
        assertTrue(fast.totalTimeMinutes < slow.totalTimeMinutes)
        assertTrue(fast.netPerHour > slow.netPerHour)
        // The distance, and so the fuel, is the same either way.
        assertEquals(slow.netEarning, fast.netEarning, 0.001)
    }

    @Test
    fun `the default trip speed is the one measured from real offers`() {
        assertEquals(24.0, RideScoreSettings.DEFAULT.tripSpeedKmph, 0.001)
        // Faster than the pickup leg: finding a customer is slower than riding.
        assertTrue(
            RideScoreSettings.DEFAULT.tripSpeedKmph >
                RideScoreSettings.DEFAULT.pickupSpeedKmph,
        )
    }
}
