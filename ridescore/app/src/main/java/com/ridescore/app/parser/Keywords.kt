package com.ridescore.app.parser

/**
 * Vocabulary used to label lines. Kept apart from the parsers so a new app can
 * reuse or extend it instead of restating it.
 */
object Keywords {

    val PICKUP = listOf(
        "pickup", "pick up", "pick-up", "away", "to pickup", "to pick up",
        "reach pickup", "pickup point", "customer is", "rider is", "from",
    )

    val DROP = listOf(
        "drop", "dropoff", "drop off", "drop-off", "destination", "dest",
        "trip", "ride distance", "journey", "to drop", "total trip",
    )

    val BONUS = listOf(
        "bonus", "incentive", "surge", "promotion", "promo", "extra",
        "additional", "tip", "boost", "peak",
    )

    val TOTAL = listOf(
        "total", "you earn", "you will earn", "earning", "earnings", "payout",
        "fare", "amount", "you get",
    )

    /** Says the bonus is already inside the headline amount. */
    val INCLUDED = listOf("incl", "includes", "including", "included")

    val TIME = listOf("min", "mins", "minute", "hr", "hour", "time")

    val RIDE_TYPES = listOf(
        "bike", "moto", "auto", "cab", "car", "sedan", "mini", "prime", "premier",
        "exec", "go", "xl", "link", "parcel", "delivery", "food", "package", "rental",
    )

    /** Lines that mark the end of an offer card. Never acted on, only used to split. */
    val ACTION_WORDS = listOf(
        "accept", "decline", "reject", "skip", "ignore", "swipe to accept",
        "slide to accept", "cancel",
    )

    fun firstHitIndex(text: String, words: List<String>): Int {
        var best = -1
        for (w in words) {
            val i = text.indexOf(w)
            if (i >= 0 && (best < 0 || i < best)) best = i
        }
        return best
    }

    fun containsAny(text: String, words: List<String>): Boolean =
        words.any { text.contains(it) }
}
