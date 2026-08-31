package com.ridescore.app.domain.speed

import com.ridescore.app.domain.settings.RideScoreSettings

/** Where a speed estimate came from, so the card can say so. */
enum class SpeedSource {
    /** Measured from offers seen in the last few minutes. The road right now. */
    LIVE,

    /** This hour of the day, learned over previous days. */
    HOUR_OF_DAY,

    /** Nothing learned yet - the shipped default. */
    DEFAULT,
}

data class SpeedEstimate(
    val anchorKmph: Double,
    val source: SpeedSource,
    val samples: Int,
)

/**
 * One reading of how fast the roads are, taken from an offer that printed both
 * a distance and a duration.
 *
 * [anchorKmph] is the observed speed divided out of its trip-length band, so a
 * 1 km hop and a 12 km run can be pooled. A 1.5 km trip at 15 km/h and a 9 km
 * trip at 32 km/h are both "a 24 km/h day".
 */
data class SpeedObservation(
    val atMillis: Long,
    val hourOfDay: Int,
    val anchorKmph: Double,
)

/**
 * Live traffic, learned from the offers themselves.
 *
 * When Rapido prints "5.96 km · 13.97 min" it has already asked its own
 * routing engine what that road costs *at this moment*. That is a live,
 * traffic-aware estimate for the exact street the driver is on, and it arrives
 * free, instantly, with no network call and nothing leaving the phone. In a
 * log of 1,894 offers there were 655 such readings over four days.
 *
 * So RideScore learns from them rather than guessing. Asked for a speed it
 * answers in this order:
 *
 *  1. **Live** - the median of readings from the last [LIVE_WINDOW_MINUTES]
 *     minutes, once there are at least [MIN_LIVE_SAMPLES] of them. Replaying
 *     that log, 68% of the offers that needed an estimate had three or more.
 *  2. **This hour** - what this hour of the day has averaged before. The same
 *     log put 05:00 at 27.3 km/h and 09:00 at 17.7, so the hour matters even
 *     when the last half hour is quiet.
 *  3. **The default** - only when nothing has been learned yet.
 *
 * Readings are kept as plain numbers: a speed, an hour, a timestamp. No
 * location, no addresses, no fares, and none of it leaves the device.
 */
data class SpeedProfile(
    /** Newest last. Capped at [MAX_RECENT]. */
    val recent: List<SpeedObservation> = emptyList(),
    /** 24 slots. Null where that hour has never been seen. */
    val hourly: List<HourStat?> = List(24) { null },
) {

    data class HourStat(val anchorKmph: Double, val samples: Int)

    /**
     * Record an offer that printed both figures.
     *
     * Readings outside [MIN_PLAUSIBLE_KMPH]..[MAX_PLAUSIBLE_KMPH] are dropped:
     * a misparse that says 100 km in 3 minutes must not be allowed to teach
     * the app that the roads are empty.
     */
    fun observe(
        tripKm: Double,
        tripMinutes: Double,
        atMillis: Long,
        hourOfDay: Int,
        settings: RideScoreSettings = RideScoreSettings.DEFAULT,
    ): SpeedProfile {
        if (tripKm <= 0.0 || tripMinutes <= 0.0) return this
        if (hourOfDay !in 0..23) return this

        val observed = tripKm / (tripMinutes / 60.0)
        if (observed < MIN_PLAUSIBLE_KMPH || observed > MAX_PLAUSIBLE_KMPH) return this

        val band = settings.tripSpeedFor(tripKm) / settings.tripSpeedKmph
        if (band <= 0.0) return this
        val anchor = observed / band
        if (anchor < MIN_PLAUSIBLE_KMPH || anchor > MAX_PLAUSIBLE_KMPH) return this

        val trimmed = (recent + SpeedObservation(atMillis, hourOfDay, anchor))
            .takeLast(MAX_RECENT)

        val was = hourly[hourOfDay]
        val now = if (was == null) {
            HourStat(anchor, 1)
        } else {
            // Weighted toward what the hour has always been, so one strange
            // ride cannot rewrite it, while a genuine change still lands
            // within a handful of readings.
            HourStat(
                anchorKmph = was.anchorKmph * (1 - HOUR_LEARNING_RATE) + anchor * HOUR_LEARNING_RATE,
                samples = was.samples + 1,
            )
        }

        return copy(
            recent = trimmed,
            hourly = hourly.toMutableList().also { it[hourOfDay] = now },
        )
    }

    /** The anchor speed to assume right now, and where the number came from. */
    fun estimate(
        nowMillis: Long,
        hourOfDay: Int,
        settings: RideScoreSettings = RideScoreSettings.DEFAULT,
    ): SpeedEstimate {
        val window = LIVE_WINDOW_MINUTES * 60_000L
        val live = recent
            .filter { nowMillis - it.atMillis in 0..window }
            .map { it.anchorKmph }
        if (live.size >= MIN_LIVE_SAMPLES) {
            return SpeedEstimate(median(live), SpeedSource.LIVE, live.size)
        }

        val hour = hourly.getOrNull(hourOfDay)
        if (hour != null && hour.samples >= MIN_HOUR_SAMPLES) {
            return SpeedEstimate(hour.anchorKmph, SpeedSource.HOUR_OF_DAY, hour.samples)
        }

        return SpeedEstimate(settings.tripSpeedKmph, SpeedSource.DEFAULT, 0)
    }

    /** Drop readings too old to mean anything, so the store stays small. */
    fun pruned(nowMillis: Long): SpeedProfile {
        val cutoff = nowMillis - RECENT_KEEP_HOURS * 3_600_000L
        val kept = recent.filter { it.atMillis in cutoff..nowMillis }
        return if (kept.size == recent.size) this else copy(recent = kept)
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    companion object {
        /** How recent a reading has to be to count as "the road right now". */
        const val LIVE_WINDOW_MINUTES = 45L

        /** One offer is an anecdote. Three in three quarters of an hour is a road. */
        const val MIN_LIVE_SAMPLES = 3

        /** Before this, an hour's average is not worth trusting over the default. */
        const val MIN_HOUR_SAMPLES = 5

        const val MAX_RECENT = 60
        const val RECENT_KEEP_HOURS = 6L

        const val HOUR_LEARNING_RATE = 0.2

        // A bike in city traffic. Outside this, the reading is a misparse.
        const val MIN_PLAUSIBLE_KMPH = 3.0
        const val MAX_PLAUSIBLE_KMPH = 70.0

        val EMPTY = SpeedProfile()
    }
}
