package com.ridescore.app.parser

/**
 * Number extraction over already-normalised text (see [TextNormalizer]).
 *
 * These are deliberately small and independent so a parser can combine them in
 * several strategies instead of relying on one exact screen layout.
 */
object Extractors {

    private const val NUM = """\d+(?:\.\d{1,3})?"""

    /** Rupee-marked amount: `₹45`, `₹ 45`, `₹128.55`. */
    private val AMOUNT = Regex("""₹\s*($NUM)""")

    /** `₹45 + ₹15`, `45 + 15`, `₹ 45 +₹ 15`. */
    private val SUM = Regex("""₹?\s*($NUM)\s*\+\s*₹?\s*($NUM)""")

    /** A number with no unit attached, e.g. the `60` in a bare fare line. */
    private val BARE_NUMBER = Regex("""(?<![\d.₹])($NUM)(?![\d.]*\s*(?:km|kms|m\b|min|mins|minutes|hr|hrs|%))""")

    private val DISTANCE =
        Regex("""($NUM)\s*(kms|km|kilometers|kilometer|meters|meter|mtrs|mtr|m)(?![a-z])""")

    private val DURATION =
        Regex("""($NUM)\s*(hours|hour|hrs|hr|h|minutes|minute|mins|min)(?![a-z])""")

    private val PERCENT = Regex("""($NUM)\s*%""")

    fun hasCurrency(line: String): Boolean = line.contains('₹')

    fun amounts(line: String): List<Double> =
        AMOUNT.findAll(line).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()

    fun bareNumbers(line: String): List<Double> =
        BARE_NUMBER.findAll(line).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()

    /** Returns base and bonus when the line reads like `₹45 + ₹15`. */
    fun sumExpression(line: String): Pair<Double, Double>? {
        val m = SUM.find(line) ?: return null
        val a = m.groupValues[1].toDoubleOrNull() ?: return null
        val b = m.groupValues[2].toDoubleOrNull() ?: return null
        return a to b
    }

    /** All distances on the line, in km. Metres are converted. */
    fun distancesKm(line: String): List<Double> =
        DISTANCE.findAll(line).mapNotNull { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            when (m.groupValues[2]) {
                "m", "meter", "meters", "mtr", "mtrs" -> v / 1000.0
                else -> v
            }
        }.filter { it > 0.0 }.toList()

    /**
     * All durations on the line, in minutes, with `1 hr 20 min` collapsed into
     * a single 80-minute value.
     */
    fun durationsMinutes(line: String): List<Double> {
        val raw = DURATION.findAll(line).mapNotNull { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            val isHour = m.groupValues[2].startsWith("h")
            Triple(if (isHour) v * 60.0 else v, isHour, m.range.first)
        }.toList()
        if (raw.isEmpty()) return emptyList()

        val out = mutableListOf<Double>()
        var i = 0
        while (i < raw.size) {
            val (value, isHour, _) = raw[i]
            if (isHour && i + 1 < raw.size && !raw[i + 1].second) {
                out += value + raw[i + 1].first
                i += 2
            } else {
                out += value
                i += 1
            }
        }
        return out.filter { it > 0.0 }
    }

    fun percents(line: String): List<Double> =
        PERCENT.findAll(line).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
}
