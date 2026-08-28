package com.ridescore.app.decision

import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.model.RideAnalysis

/**
 * Orders the offers on screen so the driver reads the best one first.
 *
 * Net rupees per hour is the primary key, because the driver's scarce resource
 * is time. Net per km breaks ties, then confidence, so a clean read wins over a
 * shaky one at the same rate. Unreadable offers (CHECK) always sink to the
 * bottom - they are shown, never ranked above a real number.
 */
object OfferRanker {

    fun rank(analyses: List<RideAnalysis>): List<RideAnalysis> =
        analyses.sortedWith(
            compareBy<RideAnalysis> { if (it.decision == Decision.CHECK) 1 else 0 }
                .thenByDescending { it.netPerHour }
                .thenByDescending { it.netPerKm }
                .thenByDescending { it.confidence },
        )
}
