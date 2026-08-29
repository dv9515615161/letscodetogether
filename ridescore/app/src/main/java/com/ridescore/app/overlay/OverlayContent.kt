package com.ridescore.app.overlay

import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.model.DecisionReason
import com.ridescore.app.domain.model.RideAnalysis
import com.ridescore.app.domain.model.ScreenAnalysis
import com.ridescore.app.domain.settings.OverlayMode
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.util.Format

/**
 * Exactly what the floating card says.
 *
 * Pure data, no Android types, so the wording and the numbers on the card are
 * unit-tested rather than eyeballed on a phone.
 *
 * Quick mode:            Detailed mode:
 * ```
 * 🟢 ACCEPT              🟢 ACCEPT
 * ₹180/hr                ₹60
 * 7.7 km • 19 min        7.7 km • 19 min
 *                        ₹112 net/hr
 *                        ₹4.59 net/km
 * ```
 */
data class OverlayContent(
    val header: String,
    val primary: String,
    /** Small line under the big number, saying what it is. */
    val primaryCaption: String? = null,
    val detailLines: List<String>,
    val otherOffers: List<String>,
    val decision: Decision,
    val lowConfidence: Boolean,
    val footer: String? = null,
)

object OverlayPresenter {

    const val MAX_RANKED_SHOWN = 3
    private const val ADVISORY = "Advisory · you decide"

    fun present(analysis: ScreenAnalysis, settings: RideScoreSettings): OverlayContent? {
        val best = analysis.best ?: return null
        val quick = settings.overlayMode == OverlayMode.QUICK
        val lowConfidence = best.reasons.contains(DecisionReason.LOW_CONFIDENCE_CAPPED) ||
            (best.decision != Decision.CHECK && best.confidence < settings.lowConfidenceThreshold)

        if (best.decision == Decision.CHECK) {
            return OverlayContent(
                header = "${Decision.CHECK.emoji} CHECK",
                primary = checkReason(best),
                detailLines = if (quick) emptyList() else partialFacts(best),
                otherOffers = emptyList(),
                decision = Decision.CHECK,
                lowConfidence = true,
                footer = if (quick) null else ADVISORY,
            )
        }

        val header = when {
            analysis.noGoodOrder && analysis.hasMultipleOffers -> "${Decision.REJECT.emoji} NO GOOD ORDER"
            analysis.hasMultipleOffers -> "${best.decision.emoji} BEST OF ${analysis.ranked.size}"
            lowConfidence -> "${Decision.MAYBE.emoji} LOW CONFIDENCE"
            else -> "${best.decision.emoji} ${best.decision.label}"
        }

        // Net rupees per hour is the headline in both modes. It is the number
        // the decision is made on, and it is the one a rider glancing down at a
        // handlebar has time to read - the fare tells them much less.
        val primary = Format.perHour(best.netPerHour)

        val details = if (quick) {
            buildList {
                if (settings.overlayShowDetailsInQuickMode) {
                    add("${Format.rupeesRounded(best.totalEarning)} · ${journeyLine(best)}")
                }
                if (best.reasons.contains(DecisionReason.PER_KM_TOO_LOW)) {
                    add("${Format.rupees2(best.netPerKm)}/km · under ${Format.rupeesRounded(settings.minNetPerKm)}")
                }
                if (best.includesIncentive) {
                    add("₹${best.grossEarning.toInt()} + ₹${best.incentiveEarning.toInt()} bonus")
                }
                if (best.includesEmptyReturn) add("incl. ride back")
            }
        } else {
            buildList {
                add("${Format.rupeesRounded(best.totalEarning)} · ${journeyLine(best)}")
                add("${Format.rupees2(best.netPerKm)} net/km")
                // Say which rule held it back, so the driver can judge whether
                // the rule is wrong rather than the offer.
                if (best.reasons.contains(DecisionReason.PER_KM_TOO_LOW)) {
                    add("under ${Format.rupeesRounded(settings.minNetPerKm)}/km target")
                }
                if (best.includesIncentive) {
                    add("₹${best.incentiveEarning.toInt()} of that is bonus")
                }
                if (best.includesEmptyReturn) {
                    add("incl. ${Format.decimal(best.returnDistanceKm)} km back empty")
                }
                best.offer.destination?.let { add("→ $it") }
            }
        }

        val others = analysis.ranked.drop(1).take(MAX_RANKED_SHOWN - 1).map { other ->
            "${other.decision.emoji} ${Format.rupeesRounded(other.grossEarning)} · " +
                "${Format.decimal(other.totalDistanceKm)} km · " +
                "${Format.minutes(other.totalTimeMinutes)} · " +
                "${Format.rupeesRounded(other.netPerHour)}/hr"
        }

        return OverlayContent(
            header = header,
            primary = primary,
            primaryCaption = if (quick) null else "net per hour, after fuel",
            detailLines = details,
            otherOffers = others,
            decision = best.decision,
            lowConfidence = lowConfidence,
            footer = if (quick) null else ADVISORY,
        )
    }

    private fun journeyLine(a: RideAnalysis): String =
        "${Format.decimal(a.totalDistanceKm)} km • ${Format.minutes(a.totalTimeMinutes)}"

    private fun checkReason(a: RideAnalysis): String {
        val missing = buildList {
            if (a.reasons.contains(DecisionReason.MISSING_FARE)) add("fare")
            if (a.reasons.contains(DecisionReason.MISSING_DISTANCE)) add("distance")
            if (a.reasons.contains(DecisionReason.MISSING_TIME)) add("time")
        }
        // Naming every missing field, not just the first, is the difference
        // between a driver knowing the app half-read the screen and thinking it
        // only missed the price.
        return if (missing.isEmpty()) "Reading unclear"
        else "Could not read " + missing.joinToString(", ")
    }

    /** Whatever was readable, so the driver is not left with nothing. */
    private fun partialFacts(a: RideAnalysis): List<String> = buildList {
        if (a.grossEarning > 0.0) add(Format.rupeesRounded(a.grossEarning))
        if (a.totalDistanceKm > 0.0 && a.totalTimeMinutes > 0.0) add(journeyLine(a))
        else if (a.totalDistanceKm > 0.0) add(Format.km(a.totalDistanceKm))
    }
}
