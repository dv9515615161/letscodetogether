package com.ridescore.app.tts

import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.model.DecisionReason
import com.ridescore.app.domain.model.ScreenAnalysis
import kotlin.math.roundToInt

/**
 * What the voice says, as plain strings.
 *
 * Short on purpose. The driver is on a bike; a sentence that runs past the few
 * seconds they have to decide is worse than silence. Digits are left as digits
 * because Android's TTS reads "112" as "one hundred twelve" already.
 */
object VoicePhrases {

    fun forAnalysis(analysis: ScreenAnalysis): String? {
        val best = analysis.best ?: return null

        if (best.decision == Decision.CHECK) {
            return when {
                best.reasons.contains(DecisionReason.MISSING_FARE) -> "Could not read fare. Check the screen."
                best.reasons.contains(DecisionReason.MISSING_DISTANCE) -> "Could not read distance. Check the screen."
                best.reasons.contains(DecisionReason.MISSING_TIME) -> "Could not read time. Check the screen."
                else -> "Reading unclear. Check the screen."
            }
        }

        val rate = best.netPerHour.roundToInt()

        if (analysis.noGoodOrder) {
            return if (analysis.hasMultipleOffers) "No good order." else "Reject, $rate net per hour."
        }

        if (analysis.hasMultipleOffers) {
            return "Best of ${analysis.ranked.size}, $rate net per hour."
        }

        return when (best.decision) {
            Decision.ACCEPT -> "Good order, $rate net per hour."
            Decision.MAYBE -> "Maybe, $rate net per hour."
            Decision.REJECT -> "Reject, $rate net per hour."
            Decision.CHECK -> null
        }
    }

    /** One utterance per distinct situation; used to avoid repeating ourselves. */
    fun signatureOf(analysis: ScreenAnalysis): String {
        val best = analysis.best ?: return "empty"
        return "${analysis.ranked.size}:${best.decision}:${best.netPerHour.roundToInt()}:${best.grossEarning.roundToInt()}"
    }
}
