package com.ridescore.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Number formats seen on Indian driver screens. */
class ExtractorsTest {

    private fun n(s: String) = TextNormalizer.normalize(s)

    @Test
    fun `sum expressions in every spacing variant`() {
        assertEquals(45.0 to 15.0, Extractors.sumExpression(n("₹45 + ₹15")))
        assertEquals(45.0 to 15.0, Extractors.sumExpression(n("₹ 45 + ₹ 15")))
        assertEquals(45.0 to 15.0, Extractors.sumExpression(n("₹45+₹15")))
        assertEquals(45.0 to 15.0, Extractors.sumExpression(n("45 + 15")))
        assertEquals(45.0 to 15.0, Extractors.sumExpression(n("Rs.45 + Rs.15")))
    }

    @Test
    fun `a plain fare is not a sum`() {
        assertNull(Extractors.sumExpression(n("₹60")))
    }

    @Test
    fun `amounts with and without decimals`() {
        assertEquals(listOf(60.0), Extractors.amounts(n("₹60")))
        assertEquals(listOf(128.55), Extractors.amounts(n("₹128.55")))
        assertEquals(listOf(1250.0), Extractors.amounts(n("₹1,250")))
        assertEquals(listOf(45.0, 15.0), Extractors.amounts(n("₹45 + ₹15")))
    }

    @Test
    fun `distances with spacing, decimals and unit variants`() {
        assertEquals(listOf(1.8), Extractors.distancesKm(n("1.8 km")))
        assertEquals(listOf(1.8), Extractors.distancesKm(n("1.8km")))
        assertEquals(listOf(1.8), Extractors.distancesKm(n("1.8 KMs")))
        assertEquals(listOf(12.0), Extractors.distancesKm(n("12 km")))
        assertEquals(listOf(0.8), Extractors.distancesKm(n("800 m")))
        assertEquals(listOf(2.1), Extractors.distancesKm(n("6 mins (2.1 km) away")))
    }

    @Test
    fun `minutes are not mistaken for metres`() {
        assertTrue(Extractors.distancesKm(n("12 mins")).isEmpty())
        assertEquals(listOf(12.0), Extractors.durationsMinutes(n("12 mins")))
        assertEquals(listOf(12.0), Extractors.durationsMinutes(n("12 min")))
        assertEquals(listOf(25.0), Extractors.durationsMinutes(n("25 minutes")))
    }

    @Test
    fun `hours are converted and combined with minutes`() {
        assertEquals(listOf(60.0), Extractors.durationsMinutes(n("1 hr")))
        assertEquals(listOf(65.0), Extractors.durationsMinutes(n("1 hr 5 min")))
        assertEquals(listOf(90.0), Extractors.durationsMinutes(n("1.5 hours")))
    }

    @Test
    fun `distance and duration on one line are both found`() {
        val line = n("23 mins (9.4 km) trip")
        assertEquals(listOf(9.4), Extractors.distancesKm(line))
        assertEquals(listOf(23.0), Extractors.durationsMinutes(line))
    }
}
