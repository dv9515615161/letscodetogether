package com.ridescore.app.parser

/**
 * Turns whatever the screen gives us into a single predictable shape before any
 * pattern matching happens.
 *
 * Indian driver apps render the same offer in many ways - "Rs.45", "₹ 45",
 * "INR 45", "1,250", Devanagari digits, non-breaking spaces - and OCR adds its
 * own noise on top. Normalising once here keeps every regex in [Extractors]
 * simple and keeps the parsers free of formatting trivia.
 */
object TextNormalizer {

    private val SPACES = charArrayOf(
        '\u00A0', '\u2000', '\u2001', '\u2002', '\u2003', '\u2004', '\u2005',
        '\u2006', '\u2007', '\u2008', '\u2009', '\u200A', '\u202F', '\u205F',
        '\u3000', '\u200B', '\uFEFF', '\t',
    )

    private val CURRENCY_WORDS = Regex("""(?:\brs\b\.?|\binr\b|\brupees?\b|₨|﷼)""")
    private val DIGIT_COMMA = Regex("""(\d),(?=\d)""")
    private val MULTI_SPACE = Regex("""\s+""")

    /** OCR confusions repaired only next to a currency symbol or a unit. */
    private val OCR_AFTER_CURRENCY = Regex("""₹\s*([0-9olis|.]+)""")
    private val OCR_BEFORE_UNIT =
        Regex("""([0-9olis|.]+)\s*(kms|km|kilometers?|meters?|mins|minutes|min|hrs|hours|hr|m)(?![a-z])""")
    private val NUMBER_SHAPE = Regex("""\d+(?:\.\d+)?""")

    private val OCR_MAP = mapOf('o' to '0', 'l' to '1', 'i' to '1', '|' to '1', 's' to '5')

    /**
     * @param ocr when true, applies conservative OCR digit repair. Never enable
     *   it for accessibility text: that text is exact and repairing it could
     *   only corrupt it.
     */
    fun normalize(raw: String, ocr: Boolean = false): String {
        if (raw.isEmpty()) return ""
        var s = raw
        for (c in SPACES) if (s.indexOf(c) >= 0) s = s.replace(c, ' ')
        s = s.lowercase()
        s = asciiDigits(s)
        s = CURRENCY_WORDS.replace(s, "₹")
        s = DIGIT_COMMA.replace(s, "$1")
        if (ocr) s = repairOcrDigits(s)
        s = MULTI_SPACE.replace(s, " ")
        return s.trim()
    }

    fun normalizeAll(raw: List<String>, ocr: Boolean = false): List<String> =
        raw.map { normalize(it, ocr) }

    /** Maps Devanagari, Telugu, Bengali ... decimal digits onto 0-9. */
    private fun asciiDigits(s: String): String {
        var needs = false
        for (c in s) if (c.code > 127 && Character.isDigit(c)) { needs = true; break }
        if (!needs) return s
        val sb = StringBuilder(s.length)
        for (c in s) {
            if (c.code > 127 && Character.isDigit(c)) {
                val d = Character.digit(c, 10)
                sb.append(if (d in 0..9) ('0' + d) else c)
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Repairs `l.8 km` -> `1.8 km` and `₹4s` -> `₹45`.
     *
     * Only runs where a number is expected (right after a rupee sign, or right
     * before a unit) and only when the run already contains at least one real
     * digit, so ordinary words are never mangled.
     */
    private fun repairOcrDigits(s: String): String {
        var out = OCR_AFTER_CURRENCY.replace(s) { m ->
            "₹" + repairRun(m.groupValues[1])
        }
        out = OCR_BEFORE_UNIT.replace(out) { m ->
            repairRun(m.groupValues[1]) + " " + m.groupValues[2]
        }
        return out
    }

    private fun repairRun(run: String): String {
        if (run.none { it.isDigit() }) return run
        val fixed = buildString(run.length) {
            for (c in run) append(OCR_MAP[c] ?: c)
        }
        // Only accept the repair if it produced something that is actually a number.
        return if (NUMBER_SHAPE.matches(fixed)) fixed else run
    }
}
