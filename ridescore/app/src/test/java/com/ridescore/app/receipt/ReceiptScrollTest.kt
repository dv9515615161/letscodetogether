package com.ridescore.app.receipt

import com.ridescore.app.TestFixtures.rapido
import com.ridescore.app.domain.receipt.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a real day's ride log exposed: **8 rides produced 40 rows**, and some of
 * those rows were nonsense.
 *
 * A receipt is on screen while the driver reads and scrolls it, and each
 * repaint is a fresh read of whatever part is visible. Two failures followed.
 *
 * **Half-rendered reads were recorded as rides.** With the top of the receipt
 * scrolled away, the first "N km" on screen is the pickup distance, so a ride
 * was written with `trip_km` equal to `pickup_km` and no duration at all:
 * 0.35, 0.67, 0.78, 2.33. A ride with no duration is worthless - the duration
 * is the whole reason the log exists - so a receipt without one is now a
 * partial render, not a ride.
 *
 * **The same ride was written over and over.** The old signature included the
 * first few lines of screen text, so a scrolled receipt looked like a
 * different ride. One ₹140.05 trip was logged eight times. A ride is now
 * identified by what makes it that ride: the app, the order's own printed
 * time, and what it paid.
 */
class ReceiptScrollTest {

    private val full = listOf(
        "Bike Order Details",
        "1:30 pm | 01 September, 2026",
        "Your Earning",
        "₹140.05",
        "13.54 km · 31.38 min",
        "Pickup and Drop info",
        "Pickup 0.35 km",
        "Payment info",
        "Customer Fare",
        "₹150",
        "Government Taxes and Other Fees",
        "-₹9.95",
    )

    /** Scrolled down: the ride-summary line is gone, the pickup is not. */
    private val scrolledPastTheSummary = listOf(
        "Bike Order Details",
        "1:30 pm | 01 September, 2026",
        "Pickup and Drop info",
        "Pickup 0.35 km",
        "Drop 13.54 km",
        "Payment info",
        "Customer Fare",
        "₹150",
        "Government Taxes and Other Fees",
        "-₹9.95",
        "Total Earning",
        "₹140.05",
    )

    @Test
    fun `the distance and the duration come from the ride's own line`() {
        val r = ReceiptParser.parse(rapido(full))!!
        assertEquals(13.54, r.tripKm!!, 0.001)
        assertEquals(31.38, r.tripMinutes!!, 0.001)
        // Not the 0.35 km pickup, which appears later on the same screen.
        assertEquals(0.35, r.pickupKm!!, 0.001)
    }

    @Test
    fun `a pickup distance is never mistaken for the trip`() {
        // Even scrolled, the drop line - not the pickup - gives the distance.
        val r = ReceiptParser.parse(rapido(scrolledPastTheSummary))
        if (r != null) {
            assertEquals(13.54, r.tripKm!!, 0.001)
        }
    }

    @Test
    fun `a receipt with no duration on screen is a partial render, not a ride`() {
        // These wrote rides of 0.35, 0.67, 0.78 and 2.33 km with no time.
        val partial = listOf(
            "Bike Order Details",
            "Pickup 0.35 km",
            "Payment info",
            "Customer Fare",
            "₹150",
            "Total Earning",
            "₹140.05",
        )
        assertNull(ReceiptParser.parse(rapido(partial)))
    }

    @Test
    fun `the same ride, read twice as the driver scrolls, is one ride`() {
        // Scrolling drops lines off the top; the ride's own line and its
        // earning stay. The old signature took the first few lines of screen
        // text, so this counted as a second ride - and one ₹140.05 trip went
        // into the log eight times.
        val afterScrolling = full.drop(1) + listOf("Total Earning", "₹140.05")

        val a = ReceiptParser.parse(rapido(full))!!
        val b = ReceiptParser.parse(rapido(afterScrolling))
        assertNotNull(b)
        assertEquals(a.signature, b!!.signature)
        assertEquals(a.totalEarning, b.totalEarning, 0.001)
    }

    @Test
    fun `two different rides that paid the same are still two rides`() {
        val other = full.map { if (it == "1:30 pm | 01 September, 2026") "2:07 pm | 01 September, 2026" else it }
        val a = ReceiptParser.parse(rapido(full))!!
        val b = ReceiptParser.parse(rapido(other))!!
        assertEquals(false, a.signature == b.signature)
    }
}
