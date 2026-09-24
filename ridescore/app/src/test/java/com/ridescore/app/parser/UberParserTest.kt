package com.ridescore.app.parser

import com.ridescore.app.TestFixtures
import com.ridescore.app.domain.model.SourceApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UberParserTest {

    private val parser = UberParser()

    @Test
    fun `reads the mins-parens-km layout`() {
        val o = parser.parse(TestFixtures.uber(TestFixtures.UBER_OFFER)).single()

        assertEquals(SourceApp.UBER, o.sourceApp)
        assertEquals(2.1, o.pickupDistanceKm!!, 0.001)
        assertEquals(6.0, o.pickupTimeMinutes!!, 0.001)
        assertEquals(9.4, o.tripDistanceKm!!, 0.001)
        assertEquals(23.0, o.tripTimeMinutes!!, 0.001)
        assertEquals("Moto", o.rideType)
        assertEquals("Kondapur Metro Station", o.pickupLocation)
    }

    @Test
    fun `a promotion already inside the fare is not added twice`() {
        val o = parser.parse(TestFixtures.uber(TestFixtures.UBER_OFFER)).single()
        assertEquals(128.55, o.totalFare!!, 0.001)
        assertEquals(20.0, o.bonusFare!!, 0.001)
        assertEquals(108.55, o.baseFare!!, 0.001)
        assertTrue(o.notes.any { it.contains("already inside") })
    }

    @Test
    fun `reads a plain upfront fare`() {
        val o = parser.parse(
            TestFixtures.uber(
                listOf("Go", "₹96", "4 mins (1.2 km) away", "18 mins (7.4 km) trip"),
            ),
        ).single()
        assertEquals(96.0, o.totalFare!!, 0.001)
        assertEquals(1.2, o.pickupDistanceKm!!, 0.001)
        assertEquals(7.4, o.tripDistanceKm!!, 0.001)
        assertEquals(18.0, o.tripTimeMinutes!!, 0.001)
        assertEquals(1.0f, o.confidence, 0.0001f)
    }

    @Test
    fun `reads a comma separated trip line`() {
        val o = parser.parse(
            TestFixtures.uber(
                listOf("Premier", "₹243.20", "Pickup 3.1 km away", "Trip 12.4 km, 25 min"),
            ),
        ).single()
        assertEquals(243.20, o.totalFare!!, 0.001)
        assertEquals(3.1, o.pickupDistanceKm!!, 0.001)
        assertEquals(12.4, o.tripDistanceKm!!, 0.001)
        assertEquals(25.0, o.tripTimeMinutes!!, 0.001)
    }
}
