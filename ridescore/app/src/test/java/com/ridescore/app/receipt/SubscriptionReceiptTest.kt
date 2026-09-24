package com.ridescore.app.receipt

import com.ridescore.app.TestFixtures.rapido
import com.ridescore.app.domain.receipt.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * A receipt from the **subscription** plan, photographed 1 September.
 *
 * It differs from the commission-plan ones in three ways that all broke the
 * first parser:
 *
 *  - There is **no "Total Earning" line at all.** The amount is at the top,
 *    under "Your Earning".
 *  - There is a **"Customer Extra"** row, and the fee is charged on the fare
 *    *plus* the extra: ₹52 + ₹10 = ₹62, and ₹5.76 is 9.29% of that.
 *  - The payment table is two columns, so the labels may arrive as a block
 *    followed by the amounts as a block, rather than interleaved.
 *
 * The arithmetic is worth recording on its own: ₹2.87 + 4.65% of ₹62 is
 * ₹5.75 against the ₹5.76 actually taken - the formula fitted from six earlier
 * payouts, confirmed to the paisa on a seventh, on the other plan.
 */
class SubscriptionReceiptTest {

    /** Labels and amounts interleaved, as a single-column reader would give them. */
    private val interleaved = listOf(
        "Bike Order Details",
        "2:17 pm | 01 September, 2026",
        "Subscription Plan 0% commission",
        "Your Earning",
        "₹56.24",
        "4.09 km · 11.3 min",
        "₹8.9 saved with subscription",
        "₹10 Extra from Customer",
        "₹1.43 Surge Fare received",
        "₹2.96 Time Fare received",
        "View Rate Card",
        "Pickup and Drop info",
        "Pickup 0.67 km",
        "Aparna HillPark Avenues, C Block, Survey No. 275 (Part), Bandam Kommu",
        "Drop 4.09 km",
        "2-38/5/42, Stalin Colony, Hyderabad, Miyapur, Telangana 500049, India",
        "Payment info",
        "Customer Fare",
        "₹52",
        "Customer Extra",
        "₹10",
        "Government Taxes and Other Fees",
        "-₹5.76",
        "Commission",
        "-₹0",
    )

    /** The same table with every label first, then every amount - two columns. */
    private val grouped = listOf(
        "Bike Order Details",
        "2:17 pm | 01 September, 2026",
        "Your Earning",
        "₹56.24",
        "4.09 km · 11.3 min",
        "₹10 Extra from Customer",
        "Pickup and Drop info",
        "Pickup 0.67 km",
        "Drop 4.09 km",
        "Payment info",
        "Customer Fare",
        "Customer Extra",
        "Government Taxes and Other Fees",
        "Commission",
        "₹52",
        "₹10",
        "-₹5.76",
        "-₹0",
    )

    @Test
    fun `it reads the receipt when the table is interleaved`() {
        val r = ReceiptParser.parse(rapido(interleaved))
        assertNotNull("interleaved receipt was not recognised at all", r)
        r!!
        assertEquals(56.24, r.totalEarning, 0.001)
        assertEquals(4.09, r.tripKm!!, 0.001)
        assertEquals(11.3, r.tripMinutes!!, 0.001)
        assertEquals(0.67, r.pickupKm!!, 0.001)
        assertEquals(52.0, r.customerFare!!, 0.001)
        assertEquals(10.0, r.customerExtra!!, 0.001)
        assertEquals(5.76, r.taxesAndFees!!, 0.001)
        assertEquals(0.0, r.commission!!, 0.001)
    }

    @Test
    fun `it reads the receipt when the table comes as two columns`() {
        val r = ReceiptParser.parse(rapido(grouped))
        assertNotNull("two-column receipt was not recognised at all", r)
        r!!
        assertEquals(56.24, r.totalEarning, 0.001)
        assertEquals(52.0, r.customerFare!!, 0.001)
        assertEquals(10.0, r.customerExtra!!, 0.001)
        assertEquals(5.76, r.taxesAndFees!!, 0.001)
        assertEquals(0.0, r.commission!!, 0.001)
    }

    @Test
    fun `the deduction is charged on the fare plus the extra`() {
        val r = ReceiptParser.parse(rapido(interleaved))!!
        // ₹62 went out, ₹56.24 arrived: ₹5.76 gone, 9.29% of the total.
        assertEquals(62.0, r.grossBeforeDeductions!!, 0.001)
        assertEquals(5.76, r.deducted!!, 0.001)

        // The formula fitted from six earlier payouts, on the other plan:
        // ₹2.87 + 4.65% of ₹62 = ₹5.75. One paisa out on a seventh receipt.
        assertEquals(2.87 + 62.0 * 0.0465, r.deducted!!, 0.02)
    }
}
