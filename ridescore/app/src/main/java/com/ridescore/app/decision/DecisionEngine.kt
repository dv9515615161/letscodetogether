package com.ridescore.app.decision

import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.model.DecisionReason
import com.ridescore.app.domain.model.RideOffer
import com.ridescore.app.domain.settings.RideScoreSettings

data class DecisionInput(
    val offer: RideOffer,
    val totalDistanceKm: Double,
    val totalTimeMinutes: Double,
    val netPerHour: Double,
    val netPerKm: Double,
    val confidence: Float,
    /** The same offer if the road is as slow as the driver's worst traffic. */
    val netPerHourInTraffic: Double = 0.0,
    /** True when the trip duration was derived from a distance, not printed. */
    val timeEstimated: Boolean = false,
)

data class DecisionOutcome(
    val decision: Decision,
    val reasons: List<DecisionReason>,
)

/**
 * The traffic light. Deterministic, local, and cheap - a handful of comparisons
 * with no I/O, so it costs microseconds.
 *
 * Rules, with the shipped defaults in brackets:
 *  - anything critical unread                       -> CHECK
 *  - net/hour below the maybe threshold [₹120]      -> REJECT
 *  - net/hour at or above the accept threshold [₹150],
 *      net/km at or above the floor [₹9], and - when the duration was
 *      estimated rather than printed - still above the accept threshold
 *      at slow-traffic speed                        -> ACCEPT
 *  - anything in between                            -> MAYBE
 *
 * Both metrics are used on purpose: a long airport run can clear ₹150/hour
 * while paying poorly per km, and a short hop can look fine per km while
 * burning the hour. When `requireBothMetrics` is off, the hourly rule decides
 * alone.
 *
 * A low-confidence read can never come out as ACCEPT.
 */
class DecisionEngine {

    fun decide(input: DecisionInput, settings: RideScoreSettings): DecisionOutcome {
        val reasons = mutableListOf<DecisionReason>()
        val offer = input.offer

        if (!offer.hasFare) reasons += DecisionReason.MISSING_FARE
        if (offer.tripDistanceKm == null || input.totalDistanceKm <= 0.0) {
            reasons += DecisionReason.MISSING_DISTANCE
        }
        if (input.totalTimeMinutes <= 0.0) reasons += DecisionReason.MISSING_TIME
        if (reasons.isNotEmpty()) return DecisionOutcome(Decision.CHECK, reasons)

        if (input.confidence < settings.minUsableConfidence) {
            return DecisionOutcome(Decision.CHECK, listOf(DecisionReason.LOW_CONFIDENCE_CAPPED))
        }

        val perKmOk = !settings.requireBothMetrics || input.netPerKm >= settings.minNetPerKm

        // An estimated duration is a promise the traffic may not keep. At
        // 09:00 this driver's real speed was 14 km/h against 30 at 07:00, so
        // an offer sold at Rs.150/hr on the average can pay Rs.94 on the day.
        // ACCEPT therefore has to survive the slow case; anything that clears
        // only on a good run is a MAYBE, which is the truthful answer.
        val survivesTraffic = !settings.requireAcceptToSurviveTraffic ||
            !input.timeEstimated ||
            input.netPerHourInTraffic >= settings.acceptNetPerHour

        val base = when {
            input.netPerHour < settings.maybeNetPerHour -> {
                reasons += DecisionReason.BELOW_MAYBE_THRESHOLD
                Decision.REJECT
            }
            input.netPerHour >= settings.acceptNetPerHour && perKmOk && survivesTraffic -> {
                reasons += DecisionReason.ABOVE_ACCEPT_THRESHOLD
                Decision.ACCEPT
            }
            input.netPerHour >= settings.acceptNetPerHour && perKmOk -> {
                reasons += DecisionReason.FAILS_IN_TRAFFIC
                Decision.MAYBE
            }
            input.netPerHour >= settings.acceptNetPerHour -> {
                reasons += DecisionReason.PER_KM_TOO_LOW
                Decision.MAYBE
            }
            else -> {
                reasons += DecisionReason.BETWEEN_THRESHOLDS
                Decision.MAYBE
            }
        }

        // Never recommend accepting something we are not sure we read correctly.
        if (base == Decision.ACCEPT && input.confidence < settings.lowConfidenceThreshold) {
            reasons += DecisionReason.LOW_CONFIDENCE_CAPPED
            return DecisionOutcome(Decision.MAYBE, reasons)
        }

        return DecisionOutcome(base, reasons)
    }
}
