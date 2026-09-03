package com.ridescore.app.domain.model

/** The traffic light shown to the driver. */
enum class Decision(val emoji: String, val label: String) {
    ACCEPT("🟢", "ACCEPT"),
    MAYBE("🟡", "MAYBE"),
    REJECT("🔴", "REJECT"),

    /** Something critical could not be read. Never shown as a recommendation. */
    CHECK("⚪", "CHECK");
}

/** Why the decision came out the way it did - surfaced in the detailed card. */
enum class DecisionReason {
    ABOVE_ACCEPT_THRESHOLD,
    BELOW_MAYBE_THRESHOLD,
    BETWEEN_THRESHOLDS,
    PER_KM_TOO_LOW,
    LOW_CONFIDENCE_CAPPED,
    MISSING_FARE,
    MISSING_DISTANCE,
    MISSING_TIME,
    /** Clears the bar at the usual speed, but not in traffic. */
    FAILS_IN_TRAFFIC,
}

/**
 * The full financial picture for one offer.
 *
 * Gross, fuel and net are kept separate on purpose. Net earning subtracts fuel
 * only, plus maintenance and platform fee **when the driver has switched those
 * options on**. It is not a claim about true profit.
 */
data class RideAnalysis(
    val offer: RideOffer,
    val totalDistanceKm: Double,
    val totalTimeMinutes: Double,
    /** What the offer itself pays. */
    val grossEarning: Double,
    /** This offer's share of a running trip-count bonus. Zero when none is set. */
    val incentiveEarning: Double = 0.0,
    val fuelCost: Double,
    val maintenanceCost: Double,
    val platformFee: Double,
    val netEarning: Double,
    val grossPerHour: Double,
    val netPerHour: Double,
    val grossPerKm: Double,
    val netPerKm: Double,
    val decision: Decision,
    val confidence: Float,
    val reasons: List<DecisionReason> = emptyList(),
    val notes: List<String> = emptyList(),
    /** True when pickup minutes were derived from pickup speed, not read on screen. */
    val pickupTimeEstimated: Boolean = false,
    /**
     * True when the *trip* minutes were derived from the trip distance because
     * the offer did not print them. Rapido often shows a fare and a distance
     * and no duration at all, and treating that as a zero-minute ride is how
     * a 12 km run comes out at Rs.4000 an hour.
     */
    val tripTimeEstimated: Boolean = false,
    /** Trip minutes counted, read or estimated. */
    val tripTimeMinutesCounted: Double = 0.0,
    /**
     * What the hour is worth if the road is as slow as this driver's worst
     * traffic. Equal to [netPerHour] whenever the offer printed its own
     * duration - there is nothing to stress-test then.
     */
    val netPerHourInTraffic: Double = 0.0,
    /** Total minutes under that same slow assumption. */
    val totalTimeMinutesInTraffic: Double = 0.0,
    /**
     * Pickup minutes actually counted in [totalTimeMinutes]. Uber prints this
     * on the offer; Rapido does not, and it is estimated there.
     */
    val pickupTimeMinutesCounted: Double = 0.0,
    /** Unpaid kilometres assumed for riding back from the drop. Zero when off. */
    val returnDistanceKm: Double = 0.0,
    /** Unpaid minutes for that ride back. */
    val returnTimeMinutes: Double = 0.0,
) {
    /** Everything the offer earns: its fare plus its share of the bonus. */
    val totalEarning: Double get() = grossEarning + incentiveEarning

    val includesIncentive: Boolean get() = incentiveEarning > 0.0
    val includesEmptyReturn: Boolean get() = returnDistanceKm > 0.0
    val isActionable: Boolean get() = decision != Decision.CHECK
    val isGood: Boolean get() = decision == Decision.ACCEPT
}

/** The result of analysing one screen: every visible offer, ranked best first. */
data class ScreenAnalysis(
    val sourceApp: SourceApp,
    val ranked: List<RideAnalysis>,
    val signature: String,
    val analysedAtMillis: Long,
    val analysisDurationMillis: Long = 0L,
    val textSource: TextSource = TextSource.ACCESSIBILITY,
) {
    val best: RideAnalysis? get() = ranked.firstOrNull()
    val hasMultipleOffers: Boolean get() = ranked.size > 1

    /** True when there is at least one readable offer and none is worth taking. */
    val noGoodOrder: Boolean
        get() = ranked.isNotEmpty() &&
            ranked.none { it.decision == Decision.ACCEPT || it.decision == Decision.MAYBE }

    companion object {
        fun empty(sourceApp: SourceApp = SourceApp.UNKNOWN) =
            ScreenAnalysis(sourceApp, emptyList(), "", 0L)
    }
}
