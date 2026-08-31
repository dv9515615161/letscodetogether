package com.ridescore.app.calculator

import com.ridescore.app.TestFixtures.offer
import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.settings.RideScoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uber and Rapido both run trip-count bonuses, and while one is live an offer
 * is worth more than its fare.
 */
class IncentiveTest {

    private val calculator = FareCalculator()

    /** ₹300 for 12 trips. */
    private val quest = RideScoreSettings.DEFAULT.copy(
        incentiveEnabled = true,
        incentiveBonus = 300.0,
        incentiveTripsTarget = 12,
        incentiveTripsDone = 0,
    )

    @Test
    fun `the share of the bonus grows as the target gets closer`() {
        assertEquals(25.0, quest.incentivePerTrip, 0.001) // 300 over 12
        assertEquals(50.0, quest.copy(incentiveTripsDone = 6).incentivePerTrip, 0.001)
        assertEquals(150.0, quest.copy(incentiveTripsDone = 10).incentivePerTrip, 0.001)
        assertEquals(300.0, quest.copy(incentiveTripsDone = 11).incentivePerTrip, 0.001)
    }

    @Test
    fun `once the bonus is earned it stops adding to offers`() {
        val done = quest.copy(incentiveTripsDone = 12)
        assertEquals(0, done.incentiveTripsRemaining)
        assertEquals(0.0, done.incentivePerTrip, 0.001)
        assertFalse(calculator.analyse(offer(), done).includesIncentive)
    }

    @Test
    fun `a poor offer becomes worth taking on the last trip of a quest`() {
        // ₹30 for a 4 km trip with a 2 km pickup: ₹32 an hour, plainly a reject.
        val poorOffer = offer(totalFare = 30.0, pickupKm = 2.0, tripKm = 4.0, tripMin = 12.0)

        val alone = calculator.analyse(poorOffer, RideScoreSettings.DEFAULT)
        assertEquals(32.40, alone.netPerHour, 0.01)
        assertEquals(Decision.REJECT, alone.decision)

        // One trip from a ₹300 bonus, the same ₹30 offer carries ₹300 with it.
        val lastTrip = calculator.analyse(poorOffer, quest.copy(incentiveTripsDone = 11))
        assertEquals(30.0, lastTrip.grossEarning, 0.001)
        assertEquals(300.0, lastTrip.incentiveEarning, 0.001)
        assertEquals(330.0, lastTrip.totalEarning, 0.001)
        assertEquals(932.40, lastTrip.netPerHour, 0.01)
        assertEquals(Decision.ACCEPT, lastTrip.decision)
        assertTrue(lastTrip.netPerHour > alone.netPerHour * 10)
    }

    @Test
    fun `commission is charged on the fare, not on the bonus`() {
        val withFee = calculator.analyse(
            offer(totalFare = 100.0),
            quest.copy(
                earningsPlan = com.ridescore.app.domain.settings.EarningsPlan.COMMISSION,
                commissionPercent = 10.0,
                gstOnCommissionPercent = 0.0,
            ),
        )
        assertEquals(10.0, withFee.platformFee, 0.001)
    }

    @Test
    fun `an unreadable offer gets no share of the bonus`() {
        val unreadable = calculator.analyse(offer(totalFare = null), quest)
        assertEquals(0.0, unreadable.incentiveEarning, 0.001)
        assertEquals(Decision.CHECK, unreadable.decision)
    }

    @Test
    fun `it is off unless the driver sets it up`() {
        assertFalse(RideScoreSettings.DEFAULT.incentiveEnabled)
        assertEquals(0.0, RideScoreSettings.DEFAULT.incentivePerTrip, 0.001)
        assertFalse(calculator.analyse(offer(), RideScoreSettings.DEFAULT).includesIncentive)
    }
}
