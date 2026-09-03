package com.ridescore.app.calculator

import com.ridescore.app.TestFixtures.offer
import com.ridescore.app.domain.settings.EarningsPlan
import com.ridescore.app.domain.settings.RideScoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checked against three real Rapido order details from the **commission**
 * plan, all Kukatpally bike rides on 29 August.
 *
 * | Customer fare | Taxes and other fees | Commission | Total earning |
 * |---|---|---|---|
 * | ₹40 | −₹5.46 | −₹6.00 | ₹28.54 |
 * | ₹82 | −₹8.29 | −₹12.72 | ₹60.99 |
 * | ₹60 | −₹6.81 | −₹9.20 | ₹43.99 |
 *
 * Two things fall out of these, and both were wrong in RideScore before.
 *
 * **The commission is not 16% of the customer fare, though the screen says
 * it is.** 16% of ₹40 is ₹6.40, not ₹6.00; of ₹82 is ₹13.12, not ₹12.72; of
 * ₹60 is ₹9.60, not ₹9.20. Short by ₹0.40 every time - the same ₹0.40, on
 * fares two apart in size. ₹0.40 is 16% of ₹2.50, so some fixed ₹2.50 of
 * each fare carries no commission, and the charge is 16% of the rest.
 *
 * **The commission line carries no GST.** It is the bare 16%. Rapido bills
 * the GST inside "Government Taxes and Other Fees", so a driver who reads
 * that line off their payout screen has already paid for it once. RideScore
 * used to add 18% on top of the commission as well, which double-counted it.
 *
 * Together those two errors had RideScore overstating the cut by ₹1.55 to
 * ₹2.76 an order - on a ₹40 ride, calling ₹13.01 gone when ₹11.46 was.
 */
class CommissionPlanPayoutTest {

    private val calculator = FareCalculator()

    /** The commission plan as it actually bills, fitted from those three. */
    private val commissionPlan = RideScoreSettings.DEFAULT.copy(
        earningsPlan = EarningsPlan.COMMISSION,
        commissionPercent = 16.0,
        commissionExemptAmount = 2.5,
        gstOnCommissionPercent = 0.0,
        // The taxes line fits Rs. 2.7648 + 6.7381% across all three, the third
        // landing within a quarter of a paisa of a line drawn through the other
        // two. Higher than the earnings plan's 4.65%, which is where the GST on
        // the commission has gone.
        taxesAndFeesPercent = 6.7381,
        perOrderFee = 2.7648,
    )

    private fun earning(fare: Double) =
        calculator.analyse(offer(totalFare = fare), commissionPlan)
            .let { it.grossEarning - it.platformFee }

    @Test
    fun `reproduces three real commission-plan payouts to within a paisa`() {
        assertEquals(28.54, earning(40.0), 0.01)
        assertEquals(60.99, earning(82.0), 0.01)
        assertEquals(43.99, earning(60.0), 0.01)
    }

    @Test
    fun `the commission line matches the screen to the paisa`() {
        assertEquals(6.00, commissionPlan.commissionOn(40.0), 0.001)
        assertEquals(12.72, commissionPlan.commissionOn(82.0), 0.001)
        assertEquals(9.20, commissionPlan.commissionOn(60.0), 0.001)
    }

    @Test
    fun `sixteen percent of the whole fare would overcharge every one of them`() {
        // What the screen's own words describe, and what RideScore used to do.
        for (fare in listOf(40.0, 82.0, 60.0)) {
            assertEquals(0.40, fare * 0.16 - commissionPlan.commissionOn(fare), 0.001)
        }
    }

    @Test
    fun `the exempt amount matters most on the smallest orders`() {
        val onSmall = commissionPlan.commissionOn(40.0) / 40.0
        val onLarge = commissionPlan.commissionOn(200.0) / 200.0
        assertTrue(onSmall < onLarge)
        // Never above the headline rate, whatever the fare.
        assertTrue(onLarge < 0.16)
    }

    @Test
    fun `a zero fare is not charged a negative commission`() {
        assertEquals(0.0, commissionPlan.commissionOn(0.0), 0.001)
        assertEquals(0.0, commissionPlan.commissionOn(1.0), 0.001)
    }

    @Test
    fun `GST entered on top of a payout-screen taxes line would double-count`() {
        val doubled = commissionPlan.copy(gstOnCommissionPercent = 18.0)
        // Rs. 1.08 more on a Rs. 40 order that was already fully accounted for.
        assertEquals(
            1.08,
            doubled.deductionOn(40.0) - commissionPlan.deductionOn(40.0),
            0.01,
        )
        assertEquals(0.0, RideScoreSettings.DEFAULT.gstOnCommissionPercent, 0.001)
    }
}
