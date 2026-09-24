package com.ridescore.app.calculator

import com.ridescore.app.TestFixtures.offer
import com.ridescore.app.domain.settings.EarningsPlan
import com.ridescore.app.domain.settings.RideScoreSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Rapido's rate card charges a percentage commission *and* a flat handling fee
 * per order. A percentage alone cannot express the second one, and the flat
 * part is what quietly ruins small orders.
 */
class PlatformFeeTest {

    private val calculator = FareCalculator()

    /**
     * A made-up commission plan: 16% of the fare, 18% GST on that, ₹5 an
     * order. Round numbers chosen to make the arithmetic below checkable by
     * hand - what Rapido actually bills is in [CommissionPlanPayoutTest],
     * measured off real order details.
     *
     * The commission-exempt amount is zeroed so these cases test the one thing
     * they are about: how GST composes with a commission.
     */
    private val withFees = RideScoreSettings.DEFAULT.copy(
        earningsPlan = EarningsPlan.COMMISSION,
        commissionPercent = 16.0,
        commissionExemptAmount = 0.0,
        gstOnCommissionPercent = 18.0,
        perOrderFee = 5.0,
    )

    @Test
    fun `GST is charged on the commission, not on the fare`() {
        // 16% with 18% GST on it is 18.88% of the fare - not 34%, which is what
        // adding the two percentages together would wrongly give.
        assertEquals(18.88, withFees.effectiveCommissionPercent, 0.001)

        val a = calculator.analyse(offer(totalFare = 100.0), withFees)
        assertEquals(23.88, a.platformFee, 0.001) // 18.88 + 5
    }

    @Test
    fun `the earnings plan takes no cut from an offer`() {
        val plan = RideScoreSettings.DEFAULT.copy(
            earningsPlan = EarningsPlan.SUBSCRIPTION,
            dailyPlanFee = 40.0,
        )
        // A day's fee is spent whichever order comes next, so it has no
        // bearing on whether this one is worth taking.
        assertEquals(0.0, plan.effectiveCommissionPercent, 0.001)
        assertEquals(0.0, calculator.analyse(offer(totalFare = 100.0), plan).platformFee, 0.001)
    }

    @Test
    fun `the commission plan is worth about a fifth of every fare`() {
        val onPlan = calculator.analyse(offer(totalFare = 62.0), withFees)
        val offPlan = calculator.analyse(offer(totalFare = 62.0), RideScoreSettings.DEFAULT)

        // The ₹46 + ₹16 offer from a real shift: ₹62 to the customer, but
        // ₹16.71 of it stays with Rapido before any petrol is bought.
        assertEquals(16.71, onPlan.platformFee, 0.01)
        assertEquals(0.0, offPlan.platformFee, 0.001)
        assertEquals(offPlan.netEarning - 16.71, onPlan.netEarning, 0.01)
    }

    @Test
    fun `a flat fee costs a small order far more than a big one`() {
        val small = calculator.analyse(offer(totalFare = 38.0), withFees)
        val large = calculator.analyse(offer(totalFare = 142.0), withFees)

        // The same ₹5 is 13% of a ₹38 order and 3.5% of a ₹142 one, so the
        // platform's total share is much larger on the small one.
        assertEquals(32.0, small.platformFee / small.grossEarning * 100, 0.2)
        assertEquals(22.4, large.platformFee / large.grossEarning * 100, 0.2)
    }

    @Test
    fun `the fee never exceeds what the order paid`() {
        val tiny = calculator.analyse(offer(totalFare = 3.0), withFees.copy(perOrderFee = 20.0))
        assertEquals(3.0, tiny.platformFee, 0.001)
    }

    @Test
    fun `nothing is deducted by default, so no cut is ever invented`() {
        val a = calculator.analyse(offer(totalFare = 100.0), RideScoreSettings.DEFAULT)
        assertEquals(0.0, a.platformFee, 0.001)
        assertEquals(EarningsPlan.SUBSCRIPTION, RideScoreSettings.DEFAULT.earningsPlan)
        assertEquals(0.0, RideScoreSettings.DEFAULT.perOrderFee, 0.001)
    }
}
