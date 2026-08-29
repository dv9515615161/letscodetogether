package com.ridescore.app.parser

import com.ridescore.app.domain.model.ScreenSnapshot
import com.ridescore.app.domain.model.TextSource

/**
 * Tells an offer apart from a trip already under way.
 *
 * A navigation screen carries a fare, a distance and a duration just like an
 * offer does, so without this it parses as one and the driver gets an advisory
 * card about a ride they are already on - useless, and a distraction on the
 * move.
 *
 * The distinguishing feature is not the words but the *affordance*: an offer
 * has something to accept. Both apps put a new offer on top of an active trip
 * when they have one - Uber offers the next trip while the current one is
 * finishing - so a screen showing "Navigate" **and** an accept button is a real
 * offer and must still be scored.
 */
object TripState {

    private val IN_PROGRESS = listOf(
        "navigate", "picking up", "start trip", "end trip", "start ride", "end ride",
        "arrived", "reached", "on trip", "cancel trip", "cancel ride",
        "enter otp", "start otp", "drop otp", "call rider", "call customer",
        "complete ride", "complete order", "collect cash",
    )

    private val ACCEPT_AFFORDANCE = listOf("accept", "confirm")

    fun looksLikeActiveTrip(snapshot: ScreenSnapshot): Boolean {
        val ocr = snapshot.textSource == TextSource.OCR
        var inProgress = false
        var canAccept = false

        for (raw in snapshot.allLines) {
            val line = TextNormalizer.normalize(raw, ocr)
            if (!inProgress && IN_PROGRESS.any { line.contains(it) }) inProgress = true
            // Only a short line is a button. A sentence mentioning "accept" is prose.
            if (!canAccept && line.length <= 24 && ACCEPT_AFFORDANCE.any { line.contains(it) }) {
                canAccept = true
            }
        }

        return inProgress && !canAccept
    }
}
