package com.ridescore.app.speed

import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.domain.speed.SpeedProfile
import com.ridescore.app.domain.speed.SpeedProfileCodec
import com.ridescore.app.domain.speed.SpeedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live traffic without a traffic API.
 *
 * When Rapido prints "5.96 km · 13.97 min" it has already asked its own
 * routing engine what that road costs at this moment. RideScore reads it off
 * the screen it is already reading, so the speed it assumes is measured, not
 * hardcoded - and no coordinates leave the phone to get it.
 *
 * The numbers below are this driver's, from 655 such readings over four days:
 *
 * | Hour | Measured anchor speed |
 * |---|---|
 * | 05:00 | 27.3 km/h |
 * | 07:00 | 25.3 km/h |
 * | 09:00 | 17.7 km/h |
 * | 23:00 | 23.9 km/h |
 *
 * Early morning really is faster, as he said. The shipped default of 24 is
 * wrong by a third at 09:00 and by a seventh at 05:00.
 */
class SpeedProfileTest {

    private val settings = RideScoreSettings.DEFAULT
    private val hour = 3_600_000L
    private val minute = 60_000L

    /** A 6 km trip taking [minutes], which lands in the "over 5 km" band. */
    private fun SpeedProfile.sawTrip(minutes: Double, atMillis: Long, hourOfDay: Int = 9) =
        observe(6.0, minutes, atMillis, hourOfDay, settings)

    @Test
    fun `nothing learned yet means the shipped default`() {
        val e = SpeedProfile.EMPTY.estimate(0L, 9, settings)
        assertEquals(SpeedSource.DEFAULT, e.source)
        assertEquals(settings.tripSpeedKmph, e.anchorKmph, 0.001)
    }

    @Test
    fun `one reading is an anecdote, three are a road`() {
        val now = 10 * hour
        var p = SpeedProfile.EMPTY.sawTrip(20.0, now - 2 * minute)
        assertEquals(SpeedSource.DEFAULT, p.estimate(now, 9, settings).source)

        p = p.sawTrip(20.0, now - 3 * minute).sawTrip(20.0, now - 4 * minute)
        val e = p.estimate(now, 9, settings)
        assertEquals(SpeedSource.LIVE, e.source)
        assertEquals(3, e.samples)
        // 6 km in 20 min is 18 km/h, in the "long trip" band worth 1.32 of the
        // anchor - so the road is behaving like a 13.6 km/h day.
        assertEquals(13.6, e.anchorKmph, 0.2)
    }

    @Test
    fun `a jam is picked up within minutes and drives the estimate down`() {
        val now = 10 * hour
        val jammed = (1..4).fold(SpeedProfile.EMPTY) { p, i ->
            p.sawTrip(26.0, now - i * minute) // 6 km in 26 min = 13.8 km/h
        }

        val live = jammed.estimate(now, 9, settings)
        assertEquals(SpeedSource.LIVE, live.source)
        assertTrue("live anchor was ${live.anchorKmph}", live.anchorKmph < 12.0)

        // And the whole speed family moves with it, without touching settings.
        val slowed = settings.copy(liveTripSpeedKmph = live.anchorKmph)
        assertTrue(slowed.tripSpeedFor(9.0) < settings.tripSpeedFor(9.0))
    }

    @Test
    fun `readings go stale, and the app stops calling them live`() {
        val now = 10 * hour
        val old = (1..4).fold(SpeedProfile.EMPTY) { p, i ->
            p.sawTrip(20.0, now - (SpeedProfile.LIVE_WINDOW_MINUTES + i) * minute)
        }
        assertNotEquals(SpeedSource.LIVE, old.estimate(now, 9, settings).source)
    }

    @Test
    fun `an hour of the day is learned and used when the last hour was quiet`() {
        var p = SpeedProfile.EMPTY
        // Five rides through the 09:00 jam, yesterday.
        repeat(5) { i -> p = p.sawTrip(26.0, i * minute, hourOfDay = 9) }

        // Today, mid-morning, nothing seen for hours.
        val e = p.estimate(48 * hour, 9, settings)
        assertEquals(SpeedSource.HOUR_OF_DAY, e.source)
        assertTrue("learned ${e.anchorKmph}", e.anchorKmph < settings.tripSpeedKmph)

        // An hour never ridden still falls back to the default.
        assertEquals(SpeedSource.DEFAULT, p.estimate(48 * hour, 3, settings).source)
    }

    @Test
    fun `early morning and peak hour are learned apart`() {
        var p = SpeedProfile.EMPTY
        repeat(6) { i -> p = p.observe(6.0, 13.0, i * minute, 5, settings) } // ~27.7 km/h
        repeat(6) { i -> p = p.observe(6.0, 26.0, i * minute, 9, settings) } // ~13.8 km/h

        val dawn = p.estimate(48 * hour, 5, settings)
        val peak = p.estimate(48 * hour, 9, settings)
        assertEquals(SpeedSource.HOUR_OF_DAY, dawn.source)
        assertEquals(SpeedSource.HOUR_OF_DAY, peak.source)
        assertTrue(
            "dawn ${dawn.anchorKmph} should beat peak ${peak.anchorKmph}",
            dawn.anchorKmph > peak.anchorKmph * 1.5,
        )
    }

    @Test
    fun `a misparse can never teach the app that the roads are empty`() {
        // The log really did contain "9.60 km" trips of "100 km" - junk.
        val p = SpeedProfile.EMPTY
            .observe(100.0, 3.0, 0L, 9, settings) // 2000 km/h
            .observe(6.0, 600.0, 0L, 9, settings) // 0.6 km/h
        assertEquals(SpeedProfile.EMPTY, p)
    }

    @Test
    fun `a short trip and a long one teach the same lesson`() {
        // 1.5 km in 6 min is 15 km/h; 9 km in 17 min is 31.8. Both are an
        // ordinary day, and both should imply about the same anchor.
        val shortTrip = SpeedProfile.EMPTY.observe(1.5, 6.0, 0L, 9, settings)
        val longTrip = SpeedProfile.EMPTY.observe(9.0, 17.0, 0L, 9, settings)

        val a = shortTrip.recent.first().anchorKmph
        val b = longTrip.recent.first().anchorKmph
        assertEquals(a, b, 1.5)
        assertEquals(24.0, a, 1.5)
    }

    @Test
    fun `old readings are pruned so the file cannot grow forever`() {
        val now = 100 * hour
        var p = SpeedProfile.EMPTY
        repeat(10) { i -> p = p.sawTrip(20.0, now - (i + 7) * hour) }
        repeat(3) { i -> p = p.sawTrip(20.0, now - i * minute) }

        assertEquals(3, p.pruned(now).recent.size)
        // Pruning recent readings never forgets the hourly averages.
        assertEquals(p.hourly, p.pruned(now).hourly)
    }

    @Test
    fun `it never keeps more than the cap`() {
        var p = SpeedProfile.EMPTY
        repeat(SpeedProfile.MAX_RECENT * 2) { i -> p = p.sawTrip(20.0, i.toLong() * 1000) }
        assertEquals(SpeedProfile.MAX_RECENT, p.recent.size)
    }

    @Test
    fun `what is learned survives a restart`() {
        var p = SpeedProfile.EMPTY
        repeat(6) { i -> p = p.sawTrip(26.0, i * minute, hourOfDay = 9) }

        val restored = SpeedProfileCodec.decode(SpeedProfileCodec.encode(p))
        assertEquals(p.recent.size, restored.recent.size)
        assertEquals(
            p.estimate(6 * minute, 9, settings).anchorKmph,
            restored.estimate(6 * minute, 9, settings).anchorKmph,
            0.05,
        )
    }

    @Test
    fun `a corrupt file costs nothing worse than re-learning`() {
        assertEquals(SpeedProfile.EMPTY, SpeedProfileCodec.decode("not a profile"))
        assertEquals(SpeedProfile.EMPTY, SpeedProfileCodec.decode(""))
        // A good file with one bad line keeps the good lines.
        val partial = SpeedProfileCodec.decode("v1\nr 1000 9 18.5\nr broken\nh 9 17.7 40\n")
        assertEquals(1, partial.recent.size)
        assertEquals(17.7, partial.hourly[9]!!.anchorKmph, 0.001)
    }

    @Test
    fun `learning can be switched off entirely`() {
        val off = settings.copy(learnRoadSpeed = false, liveTripSpeedKmph = 12.0)
        assertEquals(settings.tripSpeedKmph, off.anchorTripSpeed, 0.001)
    }
}
