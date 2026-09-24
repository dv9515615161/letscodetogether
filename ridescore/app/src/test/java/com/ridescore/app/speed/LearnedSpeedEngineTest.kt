package com.ridescore.app.speed

import com.ridescore.app.TestFixtures.rapido
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.domain.speed.SpeedProfile
import com.ridescore.app.engine.RideScoreEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The learning loop, end to end: an offer that prints its own duration teaches
 * the app how fast the road is, and the next offer that prints none is scored
 * on that instead of a hardcoded number.
 */
class LearnedSpeedEngineTest {

    private var profile = SpeedProfile.EMPTY
    private val settings = RideScoreSettings.DEFAULT

    private val engine = RideScoreEngine(
        speedObserver = { km, minutes, at ->
            profile = profile.observe(km, minutes, at, 9, settings)
        },
        liveAnchorSpeed = { at ->
            profile.estimate(at, 9, settings)
                .takeIf { it.source != com.ridescore.app.domain.speed.SpeedSource.DEFAULT }
                ?.anchorKmph
        },
    )

    /** A timed offer: 6 km in 26 minutes is a jam. */
    private fun timedOffer(at: Long) = rapido(
        listOf("Bike", "₹80", "Pickup 1.0 km", "Trip 6.0 km", "Trip time 26 mins", "Accept"),
        capturedAt = at,
    )

    /** The same ride with no time printed - the case that needs an estimate. */
    private fun untimedOffer(at: Long) = rapido(
        listOf("Bike", "₹80", "Pickup 1.0 km", "Trip 6.0 km", "Accept"),
        capturedAt = at,
    )

    @Test
    fun `an offer that shows its time teaches the app the road`() {
        assertTrue(profile.recent.isEmpty())
        engine.analyse(timedOffer(1_000L), settings)
        assertEquals(1, profile.recent.size)
    }

    @Test
    fun `an offer with no time is scored on what the road is doing`() {
        // Three timed offers through a jam.
        repeat(3) { i -> engine.analyse(timedOffer(1_000L + i * 61_000L), settings) }

        val jammed = engine.analyse(untimedOffer(200_000L), settings).ranked.first()
        val naive = com.ridescore.app.calculator.FareCalculator()
            .analyse(jammed.offer, settings)

        assertTrue(jammed.tripTimeEstimated)
        // The default would have called it 12 minutes. The road says otherwise.
        assertTrue(
            "learned ${jammed.tripTimeMinutesCounted} vs default ${naive.tripTimeMinutesCounted}",
            jammed.tripTimeMinutesCounted > naive.tripTimeMinutesCounted,
        )
        assertTrue(jammed.netPerHour < naive.netPerHour)
    }

    @Test
    fun `an offer that prints its own time is never overridden by the learning`() {
        repeat(3) { i -> engine.analyse(timedOffer(1_000L + i * 61_000L), settings) }

        val stated = engine.analyse(timedOffer(200_000L), settings).ranked.first()
        assertEquals(false, stated.tripTimeEstimated)
        assertEquals(26.0, stated.tripTimeMinutesCounted, 0.001)
    }

    @Test
    fun `switching learning off goes back to the configured speed`() {
        repeat(3) { i -> engine.analyse(timedOffer(1_000L + i * 61_000L), settings) }

        val off = settings.copy(learnRoadSpeed = false)
        val a = engine.analyse(untimedOffer(200_000L), off).ranked.first()
        assertEquals(12.0, a.tripTimeMinutesCounted, 0.001) // 6 km at 31.7 km/h
    }
}
