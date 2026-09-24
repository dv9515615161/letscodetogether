package com.ridescore.app.receipt

import com.ridescore.app.domain.log.RideCsv
import com.ridescore.app.domain.model.SourceApp
import com.ridescore.app.domain.receipt.RideReceipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** The completed-rides file: what it records and that it stays readable. */
class RideCsvTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    /** The real ₹40 → ₹28.54 ride, 1.17 km in 4.72 minutes. */
    private val receipt = RideReceipt(
        sourceApp = SourceApp.RAPIDO,
        totalEarning = 28.54,
        customerFare = 40.0,
        commission = 6.0,
        taxesAndFees = 5.46,
        tripKm = 1.17,
        tripMinutes = 4.72,
        pickupKm = 1.4,
        rideType = "Bike",
        orderTime = "8:39 am",
        signature = "sig",
    )

    private fun columns() =
        RideCsv.row(receipt, 1_756_000_000_000L, zone).split(',')

    @Test
    fun `every column has a value where the receipt had one`() {
        val header = RideCsv.COLUMNS
        val row = columns()
        assertEquals(header.size, row.size)

        val byName = header.zip(row).toMap()
        assertEquals("1.17", byName["trip_km"])
        assertEquals("4.72", byName["trip_min"])
        assertEquals("40.00", byName["customer_fare"])
        assertEquals("28.54", byName["total_earning"])
        assertEquals("RAPIDO", byName["app"])
        assertEquals("8:39 am", byName["order_time"])
    }

    @Test
    fun `it works out the things worth knowing`() {
        val byName = RideCsv.COLUMNS.zip(columns()).toMap()

        // ₹11.46 of ₹40 never arrived - 28.65%.
        assertEquals("11.46", byName["deducted"])
        assertEquals("28.65", byName["deducted_percent"])
        // The real speed of that ride: 1.17 km in 4.72 min is 14.9 km/h.
        assertEquals("14.87", byName["implied_kmph"])
        assertEquals("362.80", byName["earning_per_hour"])
    }

    @Test
    fun `a receipt missing half its fields still writes a valid row`() {
        val bare = RideReceipt(
            sourceApp = SourceApp.RAPIDO,
            totalEarning = 57.0,
            signature = "s",
        )
        val row = RideCsv.row(bare, 1_756_000_000_000L, zone)
        assertEquals(RideCsv.COLUMNS.size, row.split(',').size)
        assertTrue(row.contains("57.00"))
    }
}
