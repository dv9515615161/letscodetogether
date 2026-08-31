package com.ridescore.app.engine

import com.ridescore.app.calculator.FareCalculator
import com.ridescore.app.decision.OfferRanker
import com.ridescore.app.domain.model.ScreenAnalysis
import com.ridescore.app.domain.model.ScreenSnapshot
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.parser.ParserRegistry
import com.ridescore.app.parser.TripState

/**
 * Screen text in, ranked decisions out.
 *
 * Pure and synchronous: no Android types, no I/O, no clock beyond the injected
 * one. That is what lets the entire decision path be unit-tested and makes an
 * analysis cost well under a millisecond on a low-end phone.
 */
open class RideScoreEngine(
    private val registry: ParserRegistry = ParserRegistry(),
    private val calculator: FareCalculator = FareCalculator(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    /**
     * Called with (tripKm, tripMinutes, atMillis) for every offer that printed
     * both. The default does nothing, so the engine stays usable on its own.
     */
    private val speedObserver: (Double, Double, Long) -> Unit = { _, _, _ -> },
    /** The learned anchor speed for right now, or null if nothing is known. */
    private val liveAnchorSpeed: (Long) -> Double? = { null },
) {

    open fun analyse(snapshot: ScreenSnapshot, settings: RideScoreSettings): ScreenAnalysis {
        val started = clock()
        if (snapshot.isEmpty || !settings.watches(snapshot.sourceApp)) {
            return ScreenAnalysis.empty(snapshot.sourceApp)
        }

        // A trip already under way is not an offer. Its navigation screen has a
        // fare, a distance and a duration, and advising on a ride the driver is
        // already doing is noise in front of them while they are moving.
        if (TripState.looksLikeActiveTrip(snapshot)) {
            return ScreenAnalysis.empty(snapshot.sourceApp)
        }

        // Nor is a plan page, a rate card, a finished order's receipt or a
        // status toast. They carry rupee figures and no ride, and a card over
        // them is worse than useless - it covers the page being read.
        if (TripState.looksLikeNonOfferScreen(snapshot)) {
            return ScreenAnalysis.empty(snapshot.sourceApp)
        }

        val parser = registry.parserFor(snapshot)
            ?: return ScreenAnalysis.empty(snapshot.sourceApp)

        val offers = parser.parse(snapshot)

        // Every offer that printed its own duration is a free reading of how
        // fast the road is right now - the platform's own routing engine has
        // already asked. Learn from those before scoring anything, so a screen
        // showing one timed offer and one untimed one uses the timed one.
        if (settings.learnRoadSpeed) {
            offers.forEach { offer ->
                val km = offer.tripDistanceKm
                val minutes = offer.tripTimeMinutes
                if (km != null && minutes != null) {
                    speedObserver(km, minutes, snapshot.capturedAtMillis)
                }
            }
        }
        val resolved = if (settings.learnRoadSpeed) {
            settings.copy(liveTripSpeedKmph = liveAnchorSpeed(snapshot.capturedAtMillis))
        } else {
            settings
        }

        val analyses = offers.map { calculator.analyse(it, resolved) }
        val finished = clock()

        return ScreenAnalysis(
            sourceApp = snapshot.sourceApp,
            ranked = OfferRanker.rank(analyses),
            signature = snapshot.signature,
            analysedAtMillis = finished,
            analysisDurationMillis = finished - started,
            textSource = snapshot.textSource,
        )
    }

    /**
     * The resolved road speed, rounded, so a cache key can tell an empty road
     * from a jammed one. Zero when nothing has been learned.
     */
    open fun roadSpeedKey(atMillis: Long, settings: RideScoreSettings): Int =
        if (!settings.learnRoadSpeed) 0
        else (liveAnchorSpeed(atMillis) ?: 0.0).toInt()

    /**
     * True when accessibility text alone was not enough and an OCR pass is
     * worth its cost. Screens that produced a confident read never trigger OCR.
     */
    open fun needsOcrFallback(analysis: ScreenAnalysis, settings: RideScoreSettings): Boolean {
        if (!settings.ocrFallbackEnabled) return false
        if (analysis.textSource != com.ridescore.app.domain.model.TextSource.ACCESSIBILITY) return false
        val best = analysis.best ?: return true
        return best.confidence < settings.minUsableConfidence || !best.offer.hasFare
    }

    fun supports(snapshot: ScreenSnapshot): Boolean = registry.parserFor(snapshot) != null
}
