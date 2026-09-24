package com.ridescore.app.receipt

import com.ridescore.app.TestFixtures.rapido
import com.ridescore.app.domain.receipt.ReceiptParser
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.engine.RideScoreEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the screen that says what really happened.
 *
 * Every other number in RideScore is a forecast. The order-details screen is
 * the outcome: "1.17 km · 4.72 min" is measured, and "₹28.54" is what actually
 * arrived. These are three real ones, photographed by a driver.
 *
 * The old log shows what happens when this screen is only ignored rather than
 * read: fares of ₹197.04, ₹105.86 and ₹34.33 leaked into the offer log as
 * phantom offers, and one receipt was parsed as *two* offers at once.
 */
class ReceiptParserTest {

    /** ₹40 customer fare, −₹5.46 taxes, −₹6 commission, ₹28.54 to the driver. */
    private val bikeReceipt = listOf(
        "Bike Order Details",
        "8:39 am | 29 August, 2026",
        "Your Earning",
        "₹28.54",
        "1.17 km · 4.72 min",
        "₹7.45 Surge Fare received",
        "Pickup and Drop info",
        "Pickup 1.4 km",
        "Mana Hospitals, Road Number 2, Chaitanya Nagar, Kukatpally",
        "Drop 1.17 km",
        "28, Madhavaram Nagar Colony, Kukatpally, Hyderabad",
        "Payment info",
        "Customer Fare",
        "₹40",
        "Government Taxes and Other Fees",
        "-₹5.46",
        "Commission (16.00% of Customer Fare)",
        "-₹6",
        "Total Earning",
        "₹28.54",
    )

    @Test
    fun `it reads what the ride really earned and really took`() {
        val r = ReceiptParser.parse(rapido(bikeReceipt))
        assertNotNull(r)
        r!!

        assertEquals(28.54, r.totalEarning, 0.001)
        assertEquals(1.17, r.tripKm!!, 0.001)
        assertEquals(4.72, r.tripMinutes!!, 0.001)
        assertEquals(40.0, r.customerFare!!, 0.001)
        assertEquals(5.46, r.taxesAndFees!!, 0.001)
        assertEquals(6.0, r.commission!!, 0.001)
        assertEquals("8:39 am", r.orderTime)
        assertEquals("Bike", r.rideType)
    }

    @Test
    fun `the numbers it derives are the ones worth arguing about`() {
        val r = ReceiptParser.parse(rapido(bikeReceipt))!!

        // ₹28.54 for 4.72 minutes is ₹362 an hour - on the ride itself. The
        // hour it belongs to also holds the waiting and the ride to pickup,
        // which is why a receipt is not a shift.
        assertEquals(362.8, r.earningPerHour!!, 1.0)
        assertEquals(24.39, r.earningPerKm!!, 0.05)
        // ₹11.46 of a ₹40 fare never reached the driver: 28.7%.
        assertEquals(11.46, r.deducted!!, 0.001)
    }

    @Test
    fun `a second real receipt, with surge and waiting charges`() {
        val r = ReceiptParser.parse(
            rapido(
                listOf(
                    "Bike Order Details",
                    "8:07 am | 29 August, 2026",
                    "Your Earning",
                    "₹60.99",
                    "5.96 km · 13.97 min",
                    "₹9.15 Surge Fare received",
                    "₹6 Wait time Charges received",
                    "Payment info",
                    "Customer Fare",
                    "₹82",
                    "Government Taxes and Other Fees",
                    "-₹8.29",
                    "Commission (16.00% of Customer Fare)",
                    "-₹12.72",
                    "Total Earning",
                    "₹60.99",
                ),
            ),
        )!!

        assertEquals(60.99, r.totalEarning, 0.001)
        assertEquals(5.96, r.tripKm!!, 0.001)
        assertEquals(13.97, r.tripMinutes!!, 0.001)
        // 5.96 km in 13.97 minutes is 25.6 km/h - a real reading of the road.
        assertEquals(25.6, r.tripKm!! / (r.tripMinutes!! / 60.0), 0.2)
    }

    @Test
    fun `a live offer is never mistaken for a finished ride`() {
        // The danger is writing a ride that never happened into the log.
        val offer = rapido(
            listOf("Bike", "₹45", "Pickup 1.8 km", "Trip 5.9 km", "Trip time 12 mins", "Accept"),
        )
        assertNull(ReceiptParser.parse(offer))
    }

    @Test
    fun `an earnings summary with no ride in it is not a receipt`() {
        // ₹197.04 and ₹105.86 leaked into the old offer log from screens like
        // this. One marker is a coincidence; a receipt needs two and an amount.
        assertNull(ReceiptParser.parse(rapido(listOf("Earnings", "₹197.04", "Today"))))
        assertNull(ReceiptParser.parse(rapido(listOf("Subscription", "₹465.39 / ₹750"))))
    }

    @Test
    fun `a receipt still produces no offer card`() {
        val analysis = RideScoreEngine().analyse(rapido(bikeReceipt), RideScoreSettings.DEFAULT)
        assertTrue(analysis.ranked.isEmpty())
    }

    @Test
    fun `the same receipt repainting is recognised as the same ride`() {
        val a = ReceiptParser.parse(rapido(bikeReceipt))!!
        val b = ReceiptParser.parse(rapido(bikeReceipt))!!
        assertEquals(a.signature, b.signature)

        val other = ReceiptParser.parse(
            rapido(bikeReceipt.map { if (it == "₹28.54") "₹60.99" else it }),
        )!!
        assertTrue(a.signature != other.signature)
    }
}
