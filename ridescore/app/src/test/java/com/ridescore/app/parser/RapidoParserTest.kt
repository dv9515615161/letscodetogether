package com.ridescore.app.parser

import com.ridescore.app.TestFixtures
import com.ridescore.app.domain.model.SourceApp
import com.ridescore.app.domain.model.TextSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RapidoParserTest {

    private val parser = RapidoParser()

    @Test
    fun `reads the offer from the brief`() {
        val offers = parser.parse(TestFixtures.rapido(TestFixtures.RAPIDO_OFFER_A))
        assertEquals(1, offers.size)
        val o = offers.first()

        assertEquals(SourceApp.RAPIDO, o.sourceApp)
        assertEquals(45.0, o.baseFare!!, 0.001)
        assertEquals(15.0, o.bonusFare!!, 0.001)
        assertEquals(60.0, o.totalFare!!, 0.001)
        assertEquals(1.8, o.pickupDistanceKm!!, 0.001)
        assertEquals(5.9, o.tripDistanceKm!!, 0.001)
        assertEquals(12.0, o.tripTimeMinutes!!, 0.001)
        assertNull(o.pickupTimeMinutes)
        assertEquals("Bike", o.rideType)
        assertTrue("confidence was ${o.confidence}", o.confidence >= 0.9f)
    }

    @Test
    fun `reads a colon separated layout with locations`() {
        val offers = parser.parse(
            TestFixtures.rapido(
                listOf(
                    "₹45 + ₹15",
                    "Pickup: 1.8 km",
                    "Kondapur Metro",
                    "Drop: 5.9 km",
                    "Nallagandla",
                    "Trip time: 12 mins",
                ),
            ),
        )
        val o = offers.single()
        assertEquals(60.0, o.totalFare!!, 0.001)
        assertEquals(1.8, o.pickupDistanceKm!!, 0.001)
        assertEquals(5.9, o.tripDistanceKm!!, 0.001)
        assertEquals("Kondapur Metro", o.pickupLocation)
        assertEquals("Nallagandla", o.destination)
    }

    @Test
    fun `handles a fare with no bonus`() {
        val o = parser.parse(
            TestFixtures.rapido(
                listOf("₹60", "Pickup 1.8 km", "Trip 5.9 km", "Trip time 12 mins"),
            ),
        ).single()
        assertEquals(60.0, o.totalFare!!, 0.001)
        assertNull(o.bonusFare)
    }

    @Test
    fun `adds a separately printed bonus to the base fare`() {
        val o = parser.parse(
            TestFixtures.rapido(
                listOf("₹45", "Bonus ₹15", "Pickup 1.8 km", "Trip 5.9 km", "12 mins"),
            ),
        ).single()
        assertEquals(45.0, o.baseFare!!, 0.001)
        assertEquals(15.0, o.bonusFare!!, 0.001)
        assertEquals(60.0, o.totalFare!!, 0.001)
    }

    @Test
    fun `handles rupee formatting variants`() {
        val variants = listOf(
            listOf("Rs. 45 + Rs. 15", "Pickup 1.8 km", "Trip 5.9 km", "12 mins"),
            listOf("₹ 45 + ₹ 15", "Pickup 1.8 km", "Trip 5.9 km", "12 mins"),
            listOf("₹45+₹15", "Pickup 1.8km", "Trip 5.9km", "12min"),
        )
        for (lines in variants) {
            val o = parser.parse(TestFixtures.rapido(lines)).single()
            assertEquals(lines.toString(), 60.0, o.totalFare!!, 0.001)
            assertEquals(lines.toString(), 5.9, o.tripDistanceKm!!, 0.001)
            assertEquals(lines.toString(), 12.0, o.tripTimeMinutes!!, 0.001)
        }
    }

    @Test
    fun `falls back to screen order when nothing is labelled`() {
        val o = parser.parse(
            TestFixtures.rapido(listOf("₹60", "1.8 km", "5.9 km", "12 mins")),
        ).single()
        assertEquals(1.8, o.pickupDistanceKm!!, 0.001)
        assertEquals(5.9, o.tripDistanceKm!!, 0.001)
        // Reading by position is a guess, and it costs confidence.
        assertTrue(o.confidence < 0.95f)
        assertTrue(o.notes.any { it.contains("position") })
    }

    @Test
    fun `recovers from ocr digit noise`() {
        val o = parser.parse(
            TestFixtures.rapido(
                listOf("₹4S + ₹l5", "Pickup l.8 km", "Trip S.9 km", "Trip time l2 mins"),
                source = TextSource.OCR,
            ),
        ).single()
        assertEquals(60.0, o.totalFare!!, 0.001)
        assertEquals(1.8, o.pickupDistanceKm!!, 0.001)
        assertEquals(5.9, o.tripDistanceKm!!, 0.001)
        assertEquals(12.0, o.tripTimeMinutes!!, 0.001)
        // OCR is trusted a little less than accessibility text.
        assertTrue(o.confidence < 0.95f)
    }

    @Test
    fun `an unreadable fare is left null rather than guessed`() {
        val o = parser.parse(
            TestFixtures.rapido(
                listOf("₹ ---", "Pickup 1.8 km", "Trip 5.9 km", "Trip time 12 mins"),
            ),
        ).single()
        assertNull(o.totalFare)
        assertNotNull(o.tripDistanceKm)
        assertTrue(o.confidence < 0.75f)
    }

    @Test
    fun `ignores screens that are not offers`() {
        val snapshot = TestFixtures.rapido(listOf("Home", "Earnings", "Profile"))
        assertTrue(parser.parse(snapshot).isEmpty())
    }
}
