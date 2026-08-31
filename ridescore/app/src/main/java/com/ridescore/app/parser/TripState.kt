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

    /**
     * Screens that carry rupee figures but are not offers: the plan and
     * subscription pages, the rate card, a completed order's receipt, and the
     * status toasts.
     *
     * These are why a driver opening "Plan Details" got a black card reading
     * "CHECK - Could not read distance, time" over the page they were trying
     * to read. In a log of 1,894 offers, 153 rows carried fares no ride ever
     * paid - Rs.40,000, Rs.5,400, Rs.750, Rs.491.66 - scraped off exactly
     * these screens.
     */
    private val NOT_AN_OFFER = listOf(
        // Plan and subscription pages.
        "plan details", "activate plan", "select your next plan", "your current plan",
        "current plan", "subscription", "subscribe", "terms and conditions",
        "watch video", "know more", "per km earning", "fixed commission",
        "commission plan", "rate card", "earning plan", "earnings plan",
        // A completed order's receipt - fares, distances and times, all past.
        "order details", "payment info", "total earning", "customer fare",
        "government taxes", "fare received", "charges received",
        // Status toasts and prompts.
        "unable to go offline", "ready to go?", "go online", "go offline",
        "go to area order",
    )

    /**
     * True when the screen is one of those pages and there is nothing on it to
     * accept.
     *
     * The accept affordance is the whole safeguard, and it is not a formality:
     * Rapido's home screen shows a live offer with an "Accept" button *and* a
     * "Low Balance - Orders will be blocked" banner at the same time. A rule
     * that went by words alone would have swallowed that offer. A screen with
     * something to accept is an offer screen, whatever else is printed on it.
     */
    fun looksLikeNonOfferScreen(snapshot: ScreenSnapshot): Boolean {
        val ocr = snapshot.textSource == TextSource.OCR
        var marker = false
        var canAccept = false

        for (raw in snapshot.allLines) {
            val line = TextNormalizer.normalize(raw, ocr)
            if (!marker && NOT_AN_OFFER.any { line.contains(it) }) marker = true
            if (!canAccept && line.length <= 24 && ACCEPT_AFFORDANCE.any { line.contains(it) }) {
                canAccept = true
            }
            if (marker && canAccept) return false
        }

        return marker && !canAccept
    }

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
