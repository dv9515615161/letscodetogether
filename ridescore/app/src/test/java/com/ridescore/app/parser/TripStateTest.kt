package com.ridescore.app.parser

import com.ridescore.app.TestFixtures
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.engine.RideScoreEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A navigation screen carries a fare, a distance and a duration exactly like an
 * offer, and used to produce an advisory card for a ride already in progress.
 */
class TripStateTest {

    private val engine = RideScoreEngine()
    private val settings = RideScoreSettings.DEFAULT

    private val navigating = listOf(
        "Picking up Priya",
        "Navigate",
        "32-700/113/A",
        "Shiva Nagar, Allwyn Colony, Kukatpally, Hyderabad, Telangana",
        "₹125",
        "14.4 km",
        "41 min",
    )

    private val offer = listOf(
        "Bike Saver",
        "₹68.56",
        "4 min (0.7 km)",
        "23 mins (8.2 km)",
        "Confirm",
    )

    @Test
    fun `a trip under way is not scored`() {
        assertTrue(TripState.looksLikeActiveTrip(TestFixtures.uber(navigating)))
        assertTrue(engine.analyse(TestFixtures.uber(navigating), settings).ranked.isEmpty())
    }

    @Test
    fun `an ordinary offer is still scored`() {
        assertFalse(TripState.looksLikeActiveTrip(TestFixtures.uber(offer)))
        assertTrue(engine.analyse(TestFixtures.uber(offer), settings).ranked.isNotEmpty())
    }

    @Test
    fun `an offer arriving during a trip is still scored`() {
        // Uber offers the next trip while the current one is finishing. The
        // accept button is what makes it an offer rather than a nav screen.
        val backToBack = navigating + offer
        assertFalse(TripState.looksLikeActiveTrip(TestFixtures.uber(backToBack)))
        assertTrue(engine.analyse(TestFixtures.uber(backToBack), settings).ranked.isNotEmpty())
    }
}
