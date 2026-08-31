package com.ridescore.app.calculator

import com.ridescore.app.TestFixtures.offer
import com.ridescore.app.domain.settings.EarningsPlan
import com.ridescore.app.domain.settings.RideScoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checked against real Rapido payout screens from one morning in Kukatpally,
 * all of them on the earnings plan with **0% commission**.
 *
 * | Customer fare | Taxes and other fees | Reached the driver |
 * |---|---|---|
 * | ₹73 | −₹6.29 | ₹66.71 |
 * | ₹51 | −₹5.24 | ₹45.76 |
 * | ₹74 | −₹6.33 | ₹67.67 |
 *
 * Those three fit ₹2.82 + 4.74% of the fare to within a paisa. The point they
 * make is the one that matters: **0% commission does not mean the fare is
 * yours.** About a tenth of it is gone before any petrol is bought, and a
 * driver reading "0% commission" would never guess it.
 */
class RealPayoutTest {

    private val calculator = FareCalculator()

    /** The earnings plan as it actually pays out, fitted from those orders. */
    private val earningsPlan = RideScoreSettings.DEFAULT.copy(
        earningsPlan = EarningsPlan.SUBSCRIPTION,
        taxesAndFeesPercent = 4.74,
        perOrderFee = 2.82,
    )

    private fun payout(fare: Double) =
        calculator.analyse(offer(totalFare = fare), earningsPlan).let { it.grossEarning - it.platformFee }

    @Test
    fun `reproduces three real payouts to within a paisa`() {
        assertEquals(66.71, payout(73.0), 0.02)
        assertEquals(45.76, payout(51.0), 0.02)
        assertEquals(67.67, payout(74.0), 0.02)
    }

    @Test
    fun `zero commission still takes about a tenth of the fare`() {
        assertEquals(0.0, earningsPlan.effectiveCommissionPercent, 0.001)

        val kept = calculator.analyse(offer(totalFare = 51.0), earningsPlan).platformFee
        assertEquals(5.24, kept, 0.02)
        // 10.3% of a ₹51 order, with the commission at zero.
        assertTrue(kept / 51.0 > 0.10)
    }

    @Test
    fun `the flat part bites hardest on the smallest orders`() {
        val onSmall = calculator.analyse(offer(totalFare = 51.0), earningsPlan)
        val onLarge = calculator.analyse(offer(totalFare = 74.0), earningsPlan)

        assertTrue(
            onSmall.platformFee / onSmall.grossEarning >
                onLarge.platformFee / onLarge.grossEarning,
        )
    }

    @Test
    fun `the commission plan stacks on top of the same taxes`() {
        val commission = earningsPlan.copy(
            earningsPlan = EarningsPlan.COMMISSION,
            commissionPercent = 16.0,
            gstOnCommissionPercent = 18.0,
        )
        // 18.88% commission and GST, plus the 4.74% that is taken either way.
        assertEquals(23.62, commission.totalDeductionPercent, 0.01)

        val kept = calculator.analyse(offer(totalFare = 73.0), commission).platformFee
        assertEquals(20.06, kept, 0.02) // vs ₹6.29 on the earnings plan
    }

    @Test
    fun `nothing is deducted until the driver enters their own numbers`() {
        val untouched = calculator.analyse(offer(totalFare = 73.0), RideScoreSettings.DEFAULT)
        assertEquals(0.0, untouched.platformFee, 0.001)
    }
}
