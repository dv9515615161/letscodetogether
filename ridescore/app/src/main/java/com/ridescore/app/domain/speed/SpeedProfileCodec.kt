package com.ridescore.app.domain.speed

/**
 * The on-disk form of a [SpeedProfile]: a version line, then one line per
 * reading. Plain text so it can be read by eye when something looks wrong, and
 * pure Kotlin so the round trip is unit-tested rather than discovered broken
 * after a week of learning has been thrown away.
 *
 * Anything unparseable is skipped rather than thrown, and an unknown version
 * yields an empty profile: a corrupt file must cost the driver nothing worse
 * than re-learning over the next few offers.
 */
object SpeedProfileCodec {

    private const val VERSION = "v1"

    fun encode(profile: SpeedProfile): String = buildString {
        appendLine(VERSION)
        profile.recent.forEach { o ->
            appendLine("r ${o.atMillis} ${o.hourOfDay} ${fmt(o.anchorKmph)}")
        }
        profile.hourly.forEachIndexed { hour, stat ->
            if (stat != null) appendLine("h $hour ${fmt(stat.anchorKmph)} ${stat.samples}")
        }
    }

    fun decode(text: String): SpeedProfile {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.firstOrNull() != VERSION) return SpeedProfile.EMPTY

        val recent = mutableListOf<SpeedObservation>()
        val hourly = MutableList<SpeedProfile.HourStat?>(24) { null }

        for (line in lines.drop(1)) {
            val parts = line.split(' ')
            when {
                parts.size == 4 && parts[0] == "r" -> {
                    val at = parts[1].toLongOrNull() ?: continue
                    val hour = parts[2].toIntOrNull()?.takeIf { it in 0..23 } ?: continue
                    val speed = parts[3].toDoubleOrNull()?.takeIf { it > 0.0 } ?: continue
                    recent += SpeedObservation(at, hour, speed)
                }
                parts.size == 4 && parts[0] == "h" -> {
                    val hour = parts[1].toIntOrNull()?.takeIf { it in 0..23 } ?: continue
                    val speed = parts[2].toDoubleOrNull()?.takeIf { it > 0.0 } ?: continue
                    val samples = parts[3].toIntOrNull()?.takeIf { it > 0 } ?: continue
                    hourly[hour] = SpeedProfile.HourStat(speed, samples)
                }
            }
        }

        return SpeedProfile(
            recent = recent.takeLast(SpeedProfile.MAX_RECENT),
            hourly = hourly,
        )
    }

    private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.2f", v)
}
