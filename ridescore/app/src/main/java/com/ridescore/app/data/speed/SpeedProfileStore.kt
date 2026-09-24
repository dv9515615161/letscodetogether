package com.ridescore.app.data.speed

import android.content.Context
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.domain.speed.SpeedEstimate
import com.ridescore.app.domain.speed.SpeedProfile
import com.ridescore.app.domain.speed.SpeedProfileCodec
import com.ridescore.app.domain.speed.SpeedSource
import java.io.File
import java.util.Calendar

/**
 * Keeps what has been learned about road speed, on this phone only.
 *
 * Deliberately a small plain file rather than DataStore: the hot path reads it
 * on every offer and must never wait on a coroutine, so the profile is held in
 * memory behind a volatile field and written back at most once every
 * [WRITE_EVERY_MS]. Losing the last few readings to a crash costs nothing -
 * they are re-learned from the next few offers.
 *
 * What is stored is a list of numbers: a speed, an hour, a timestamp. There
 * are no addresses, no coordinates, no fares and no ride identifiers in it,
 * and it is never uploaded.
 */
class SpeedProfileStore(
    private val file: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    @Volatile
    private var profile: SpeedProfile = load()

    @Volatile
    private var lastWrite: Long = 0L

    fun current(): SpeedProfile = profile

    /**
     * Record an offer that printed both a distance and a duration.
     *
     * Safe to call from the analysis thread: it swaps one field and only
     * occasionally touches the disk.
     */
    fun observe(
        tripKm: Double,
        tripMinutes: Double,
        atMillis: Long,
        settings: RideScoreSettings = RideScoreSettings.DEFAULT,
    ) {
        val at = if (atMillis > 0L) atMillis else clock()
        val updated = profile.observe(tripKm, tripMinutes, at, hourOf(at), settings)
        if (updated === profile) return
        profile = updated

        val now = clock()
        if (now - lastWrite >= WRITE_EVERY_MS) {
            lastWrite = now
            save(updated.pruned(now))
        }
    }

    /** The anchor speed for right now, or null when nothing has been learned. */
    fun liveAnchorSpeed(
        atMillis: Long,
        settings: RideScoreSettings = RideScoreSettings.DEFAULT,
    ): Double? {
        val at = if (atMillis > 0L) atMillis else clock()
        val estimate = estimate(at, settings)
        return if (estimate.source == SpeedSource.DEFAULT) null else estimate.anchorKmph
    }

    fun estimate(
        atMillis: Long = clock(),
        settings: RideScoreSettings = RideScoreSettings.DEFAULT,
    ): SpeedEstimate = profile.estimate(atMillis, hourOf(atMillis), settings)

    /** Forget everything, for the settings screen. */
    fun reset() {
        profile = SpeedProfile.EMPTY
        runCatching { if (file.exists()) file.delete() }
    }

    fun flush() {
        lastWrite = clock()
        save(profile.pruned(clock()))
    }

    private fun hourOf(atMillis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = atMillis }.get(Calendar.HOUR_OF_DAY)

    private fun save(p: SpeedProfile) {
        runCatching { file.writeText(SpeedProfileCodec.encode(p)) }
    }

    private fun load(): SpeedProfile = runCatching {
        if (!file.exists()) SpeedProfile.EMPTY else SpeedProfileCodec.decode(file.readText())
    }.getOrDefault(SpeedProfile.EMPTY)

    companion object {
        const val FILE_NAME = "road_speed.txt"

        /** A shift produces a few readings a minute; once a minute is plenty. */
        const val WRITE_EVERY_MS = 60_000L
    }
}
