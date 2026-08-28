package com.ridescore.app.engine

import com.ridescore.app.calculator.FareCalculator
import com.ridescore.app.decision.OfferRanker
import com.ridescore.app.domain.model.ScreenAnalysis
import com.ridescore.app.domain.model.ScreenSnapshot
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.parser.ParserRegistry

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
) {

    open fun analyse(snapshot: ScreenSnapshot, settings: RideScoreSettings): ScreenAnalysis {
        val started = clock()
        if (snapshot.isEmpty || !settings.watches(snapshot.sourceApp)) {
            return ScreenAnalysis.empty(snapshot.sourceApp)
        }

        val parser = registry.parserFor(snapshot)
            ?: return ScreenAnalysis.empty(snapshot.sourceApp)

        val offers = parser.parse(snapshot)
        val analyses = offers.map { calculator.analyse(it, settings) }
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
