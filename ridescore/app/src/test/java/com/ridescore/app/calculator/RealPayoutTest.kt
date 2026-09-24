package com.ridescore.app.calculator

import com.ridescore.app.TestFixtures.offer
import com.ridescore.app.domain.settings.EarningsPlan
import com.ridescore.app.domain.settings.RideScoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checked against real Rapido payout screens from one day in Kukatpally, all
 * on the earnings plan with **0% commission**.
 *
 * | Customer fare | Taxes and other fees | Reached the driver |
 * |---|---|---|
 * | ₹43 | −₹4.86 | ₹38.14 |
 * | ₹51 | −₹5.24 | ₹45.76 |
 * | ₹56 | −₹5.48 | ₹50.52 |
 * | ₹73 | −₹6.29 | ₹66.71 |
 * | ₹74 | −₹6.33 | ₹67.67 |
 * | ₹89 + ₹10 extra | −₹7.46 | ₹95.54 |
 *
 * Six orders across a 2.3x range of fares fit **₹2.87 + 4.65%** to within two
 * paise, and the last one shows the fee tracks the customer extra as well as
 * the fare. The point they make is the one that matters: **0% commission does
 * not mean the fare is yours.** About a tenth is gone before any petrol is
 * bought, and a driver reading "0% commission" would never guess it.
 *
 * Parcel orders are the exception - ₹57 paid ₹57, ₹130 paid ₹130, nothing
 * deducted at all. Both were on this same 0%-commission plan, so what they
 * establish is that the *tax* and the flat fee are absent; they cannot say
 * whether a commission plan would still take its cut, so RideScore keeps
 * charging that one.
 */
class RealPayoutTest {

    private val calculator = FareCalculator()

    /** The earnings plan as it actually pays out, fitted from those orders. */
    private val earningsPlan = RideScoreSettings.DEFAULT.copy(
        earningsPlan = EarningsPlan.SUBSCRIPTION,
        taxesAndFeesPercent = 4.65,
        perOrderFee = 2.87,
    )

    private fun payout(fare: Double) =
        calculator.analyse(offer(totalFare = fare), earningsPlan).let { it.grossEarning - it.platformFee }

    @Test
    fun `reproduces six real payouts to within three paise`() {
        assertEquals(38.14, payout(43.0), 0.03)
        assertEquals(45.76, payout(51.0), 0.03)
        assertEquals(50.52, payout(56.0), 0.03)
        assertEquals(66.71, payout(73.0), 0.03)
        assertEquals(67.67, payout(74.0), 0.03)
        // ₹89 fare plus a ₹10 customer extra: the fee tracks the total.
        assertEquals(91.54, payout(99.0), 0.03)
    }

    @Test
    fun `a parcel order on the zero-commission plan keeps its whole fare`() {
        val parcel = offer(totalFare = 130.0).copy(rideType = "Parcel Delivery")
        val analysis = calculator.analyse(parcel, earningsPlan)

        assertTrue(parcel.looksLikeParcel)
        assertEquals(0.0, analysis.platformFee, 0.001)
        assertEquals(130.0, analysis.grossEarning, 0.001)
        assertTrue(analysis.notes.any { it.contains("Parcel order") })

        // The same fare as a bike ride loses about a tenth.
        val ride = calculator.analyse(offer(totalFare = 130.0), earningsPlan)
        assertEquals(8.92, ride.platformFee, 0.02)
    }

    @Test
    fun `a parcel on the commission plan still pays commission, but no tax`() {
        val commissionPlan = RideScoreSettings.DEFAULT.copy(
            earningsPlan = EarningsPlan.COMMISSION,
            commissionPercent = 16.0,
            commissionExemptAmount = 2.5,
            gstOnCommissionPercent = 0.0,
            taxesAndFeesPercent = 6.7381,
            perOrderFee = 2.7648,
        )
        val parcel = offer(totalFare = 130.0).copy(rideType = "Parcel Delivery")

        // 16% of (130 - 2.5) = 20.40 - the commission alone, and nothing else.
        assertEquals(20.40, calculator.analyse(parcel, commissionPlan).platformFee, 0.02)

        // A bike ride of the same fare pays that plus 6.74% + Rs.2.76 = 11.52.
        val ride = calculator.analyse(offer(totalFare = 130.0), commissionPlan)
        assertEquals(20.40 + 11.52, ride.platformFee, 0.03)
    }

    @Test
    fun `a bike ride is not mistaken for a parcel`() {
        assertFalse(offer(totalFare = 56.0).copy(rideType = "Bike").looksLikeParcel)
        assertEquals(5.48, calculator.analyse(
            offer(totalFare = 56.0).copy(rideType = "Bike"), earningsPlan,
        ).platformFee, 0.02)
    }

    @Test
    fun `zero commission still takes about a tenth of the fare`() {
        assertEquals(0.0, earningsPlan.effectiveCommissionPercent, 0.001)

        val kept = calculator.analyse(offer(totalFare = 51.0), earningsPlan).platformFee
        assertEquals(5.24, kept, 0.03)
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
    fun `the same fare is worth far less on the commission plan`() {
        // Both sides measured, not assumed: the earnings-plan figures above,
        // and the commission-plan ones from CommissionPlanPayoutTest.
        val commission = RideScoreSettings.DEFAULT.copy(
            earningsPlan = EarningsPlan.COMMISSION,
            commissionPercent = 16.0,
            commissionExemptAmount = 2.5,
            gstOnCommissionPercent = 0.0,
            taxesAndFeesPercent = 6.7381,
            perOrderFee = 2.7648,
        )

        val onEarnings = calculator.analyse(offer(totalFare = 73.0), earningsPlan).platformFee
        val onCommission = calculator.analyse(offer(totalFare = 73.0), commission).platformFee

        assertEquals(6.29, onEarnings, 0.03)
        assertEquals(18.96, onCommission, 0.05)
        // A ₹73 ride hands over about ₹12.70 more on the commission plan, which
        // is what the daily plan fee is buying.
        assertTrue(onCommission - onEarnings > 12.0)
    }

    @Test
    fun `nothing is deducted until the driver enters their own numbers`() {
        val untouched = calculator.analyse(offer(totalFare = 73.0), RideScoreSettings.DEFAULT)
        assertEquals(0.0, untouched.platformFee, 0.001)
    }
}
