package com.ridescore.app.parser

import com.ridescore.app.TestFixtures.rapido
import com.ridescore.app.calculator.FareCalculator
import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.engine.RideScoreEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ₹45 Rapido delivery the driver reported as "low confidence".
 *
 * The screen shows everything that decides the money: a fare, a pickup
 * distance, a drop distance, the ride type and an Accept button. The one thing
 * it does not print is a duration - which is simply how Rapido lays these out.
 *
 * Confidence used to charge 0.20 for a missing duration, which dropped an
 * otherwise clean read to 0.75 and, with any parsing penalty at all, under the
 * 0.75 bar that caps a result at MAYBE. So the app hedged on an offer it had
 * read perfectly.
 *
 * The weights now follow what the money depends on: fare 0.45, trip distance
 * 0.30, pickup distance 0.15, and only 0.10 between the two durations, which
 * are estimated from the distances and marked with a tilde when they are.
 */
class DeliveryOfferConfidenceTest {

    private val engine = RideScoreEngine()
    private val calculator = FareCalculator()

    private val screen = listOf(
        "Delivery",
        "₹45",
        "(Rapido)",
        "0.6 km",
        "Balanagar - Dreams Biryani, A/544, Allwyne Colony, 1st Phase, Circle 24, Kukatpally",
        "1.9 km",
        "Balanagar - Keerthana house, Ground floor, Jagadgiri Gutta, Hyderabad",
        "Accept",
    )

    @Test
    fun `it reads the fare and both legs`() {
        val offer = engine.analyse(rapido(screen), RideScoreSettings.DEFAULT).ranked.first().offer
        assertEquals(45.0, offer.totalFare!!, 0.001)
        assertEquals(0.6, offer.pickupDistanceKm!!, 0.001)
        assertEquals(1.9, offer.tripDistanceKm!!, 0.001)
    }

    @Test
    fun `a missing duration no longer holds it below the confidence bar`() {
        val settings = RideScoreSettings.DEFAULT
        val offer = engine.analyse(rapido(screen), settings).ranked.first().offer

        assertTrue(
            "read at ${offer.confidence}, needs ${settings.minUsableConfidence} to be scored",
            offer.confidence >= settings.minUsableConfidence,
        )
        assertTrue(
            "read at ${offer.confidence}, capped below ${settings.lowConfidenceThreshold}",
            offer.confidence >= settings.lowConfidenceThreshold,
        )
    }

    @Test
    fun `so it gets a real answer instead of CHECK`() {
        val analysis = engine.analyse(rapido(screen), RideScoreSettings.DEFAULT).ranked.first()
        assertTrue(analysis.decision != Decision.CHECK)
        assertTrue(analysis.tripTimeEstimated)
        // A 1.9 km hop is short work: 24 x 0.62 = 14.9 km/h, so 8 minutes,
        // plus 3 to reach a 0.6 km pickup at 17.
        assertEquals(8.0, analysis.tripTimeMinutesCounted, 0.001)
        assertEquals(11.0, analysis.totalTimeMinutes, 0.001)
    }

    @Test
    fun `it is a MAYBE, not an ACCEPT, because the time was guessed`() {
        val analysis = engine.analyse(rapido(screen), RideScoreSettings.DEFAULT).ranked.first()

        // ₹202/hr if the road runs, ₹139 if it does not. The bar is ₹150, so
        // this one is only worth taking on a good run - which is a MAYBE, and
        // the card says "₹139/hr in traffic" underneath so the driver can see
        // why. Nothing else holds it back: ₹14.80/km clears the ₹9 floor.
        assertEquals(201.8, analysis.netPerHour, 0.5)
        assertEquals(138.8, analysis.netPerHourInTraffic, 0.5)
        assertEquals(14.8, analysis.netPerKm, 0.05)
        assertEquals(Decision.MAYBE, analysis.decision)

        // Turn the stress test off and it is the ACCEPT it looks like.
        val relaxed = engine.analyse(
            rapido(screen),
            RideScoreSettings.DEFAULT.copy(requireAcceptToSurviveTraffic = false),
        ).ranked.first()
        assertEquals(Decision.ACCEPT, relaxed.decision)
    }

    @Test
    fun `a stated duration is still worth more than none`() {
        val timed = screen + "5 mins"
        val withTime = engine.analyse(rapido(timed), RideScoreSettings.DEFAULT).ranked.first().offer
        val without = engine.analyse(rapido(screen), RideScoreSettings.DEFAULT).ranked.first().offer
        assertTrue(withTime.confidence >= without.confidence)
    }

    @Test
    fun `a fare alone is still not enough to score`() {
        // The weights moved; the floor did not. No distance, no answer.
        val bare = listOf("Delivery", "₹45", "(Rapido)", "Accept")
        val ranked = engine.analyse(rapido(bare), RideScoreSettings.DEFAULT).ranked
        if (ranked.isNotEmpty()) {
            assertEquals(Decision.CHECK, ranked.first().decision)
        }
    }

    @Test
    fun `the weights still add to one`() {
        assertEquals(
            1.0f,
            BaseOfferParser.W_FARE + BaseOfferParser.W_TRIP_KM + BaseOfferParser.W_TRIP_MIN +
                BaseOfferParser.W_PICKUP_KM + BaseOfferParser.W_PICKUP_MIN,
            0.0001f,
        )
    }
}
