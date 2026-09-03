package com.ridescore.app.log

import com.ridescore.app.TestFixtures
import com.ridescore.app.domain.log.OfferCsv
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.engine.RideScoreEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class OfferCsvTest {

    private val engine = RideScoreEngine()
    private val settings = RideScoreSettings.DEFAULT
    private val kolkata = ZoneId.of("Asia/Kolkata")

    /** A Saturday evening in Hyderabad, built rather than hardcoded. */
    private val evening = java.time.ZonedDateTime
        .of(2026, 8, 29, 21, 35, 0, 0, kolkata)
        .toInstant()
        .toEpochMilli()

    private fun rows(lines: List<String>, at: Long = evening) =
        OfferCsv.rows(
            engine.analyse(TestFixtures.rapido(lines), settings),
            settings,
            screenId = "abc123",
            atMillis = at,
            zone = kolkata,
        )

    private fun cells(row: String): List<String> {
        // Minimal CSV reader, enough to check the writer.
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < row.length) {
            val c = row[i]
            when {
                inQuotes && c == '"' && i + 1 < row.length && row[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        out += current.toString()
        return out
    }

    @Test
    fun `every row has one cell per column`() {
        val row = rows(TestFixtures.RAPIDO_OFFER_A).single()
        assertEquals(OfferCsv.COLUMNS.size, cells(row).size)
        assertEquals(OfferCsv.COLUMNS.size, OfferCsv.header().split(",").size)
    }

    @Test
    fun `the offer from the brief is written out correctly`() {
        val row = cells(rows(TestFixtures.RAPIDO_OFFER_A).single())
        val column = { name: String -> row[OfferCsv.COLUMNS.indexOf(name)] }

        assertEquals("RAPIDO", column("app"))
        assertEquals("REJECT", column("decision"))
        assertEquals("45.00", column("base_fare"))
        assertEquals("15.00", column("bonus_fare"))
        assertEquals("60.00", column("total_fare"))
        assertEquals("1.80", column("pickup_km"))
        assertEquals("5.90", column("trip_km"))
        assertEquals("7.70", column("total_km"))
        assertEquals("12.00", column("trip_min"))
        assertEquals("19.00", column("total_min"))
        assertEquals("24.64", column("fuel_cost"))
        assertEquals("35.36", column("net_earning"))
        assertEquals("111.66", column("net_per_hour"))
        assertEquals("4.59", column("net_per_km"))
        assertEquals("true", column("pickup_min_estimated"))
    }

    @Test
    fun `time of day is broken out for pattern analysis`() {
        val row = cells(rows(TestFixtures.RAPIDO_OFFER_A).single())
        val column = { name: String -> row[OfferCsv.COLUMNS.indexOf(name)] }

        assertEquals("2026-08-29", column("date"))
        assertEquals("21", column("hour"))
        assertEquals("Saturday", column("weekday"))
    }

    @Test
    fun `the settings in force are recorded with the row`() {
        val row = cells(rows(TestFixtures.RAPIDO_OFFER_A).single())
        val column = { name: String -> row[OfferCsv.COLUMNS.indexOf(name)] }

        // Without these, a row stops meaning anything once a setting changes.
        assertEquals("37.50", column("mileage_kmpl"))
        assertEquals("120.00", column("petrol_price"))
        assertEquals("150.00", column("accept_per_hour"))
    }

    @Test
    fun `addresses full of commas survive the round trip`() {
        val address = "Kukatpally - 24-230, Kukatpally House Phase 1, Balanagar, 500072"
        val row = cells(
            rows(
                listOf("Bike", "₹45 + ₹18", "Pickup 3.5 km", address, "Drop 5.9 km", "13 mins"),
            ).single(),
        )
        assertEquals(OfferCsv.COLUMNS.size, row.size)
        assertEquals(address, row[OfferCsv.COLUMNS.indexOf("pickup_location")])
    }

    @Test
    fun `quotes inside a value do not break the row`() {
        assertEquals("\"He said \"\"hi\"\", then left\"", OfferCsv.escape("He said \"hi\", then left"))
        assertEquals("plain", OfferCsv.escape("plain"))
        assertEquals("", OfferCsv.escape(""))
    }

    @Test
    fun `every offer on a screen gets its own ranked row`() {
        val rows = rows(TestFixtures.RAPIDO_OFFER_A + TestFixtures.RAPIDO_OFFER_B)
        assertEquals(2, rows.size)

        val rank = OfferCsv.COLUMNS.indexOf("rank")
        val onScreen = OfferCsv.COLUMNS.indexOf("offers_on_screen")
        assertEquals("1", cells(rows[0])[rank])
        assertEquals("2", cells(rows[1])[rank])
        assertTrue(rows.all { cells(it)[onScreen] == "2" })

        // Rows from one screen share an id, so they can be grouped later.
        val screen = OfferCsv.COLUMNS.indexOf("screen_id")
        assertEquals(cells(rows[0])[screen], cells(rows[1])[screen])
    }

    @Test
    fun `a field that could not be read is left empty, not zero`() {
        val rows = rows(listOf("Bike", "Pickup 1.8 km", "Trip 5.9 km", "Trip time 12 mins"))
        val row = cells(rows.single())
        assertEquals("", row[OfferCsv.COLUMNS.indexOf("total_fare")])
        assertEquals("CHECK", row[OfferCsv.COLUMNS.indexOf("decision")])
    }
}
