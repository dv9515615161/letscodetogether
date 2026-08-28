package com.ridescore.app.domain.model

/**
 * A single ride/order offer as read off the screen.
 *
 * Every numeric field is nullable on purpose: RideScore never invents a value it
 * could not read. A missing field lowers [confidence] and, if it is a critical
 * field, forces a [Decision.CHECK] instead of a confident ACCEPT.
 */
data class RideOffer(
    val sourceApp: SourceApp,
    val baseFare: Double? = null,
    val bonusFare: Double? = null,
    val totalFare: Double? = null,
    val pickupDistanceKm: Double? = null,
    val tripDistanceKm: Double? = null,
    val tripTimeMinutes: Double? = null,
    val pickupTimeMinutes: Double? = null,
    val pickupLocation: String? = null,
    val destination: String? = null,
    val rideType: String? = null,
    val timestamp: Long = 0L,
    /** 0f..1f, how sure the parser is about this extraction. */
    val confidence: Float = 0f,
    /** Per-field extraction quality, useful for debugging and for the UI. */
    val fieldConfidence: Map<OfferField, Float> = emptyMap(),
    /** Where the text came from, for diagnostics. */
    val textSource: TextSource = TextSource.ACCESSIBILITY,
    /** Human-readable parser notes ("pickup/trip distance assigned by order"). */
    val notes: List<String> = emptyList(),
    /** The raw lines this offer was parsed from. Never leaves the device. */
    val rawLines: List<String> = emptyList(),
) {
    /** Fare is the one thing that can never be guessed. */
    val hasFare: Boolean get() = totalFare != null && totalFare > 0.0

    /** Enough to compute a meaningful net rupees/hour and rupees/km. */
    val hasCriticalData: Boolean
        get() = hasFare &&
            (tripDistanceKm != null && tripDistanceKm > 0.0) &&
            (tripTimeMinutes != null && tripTimeMinutes > 0.0)
}

enum class OfferField {
    BASE_FARE,
    BONUS_FARE,
    TOTAL_FARE,
    PICKUP_DISTANCE,
    TRIP_DISTANCE,
    TRIP_TIME,
    PICKUP_TIME,
    PICKUP_LOCATION,
    DESTINATION,
    RIDE_TYPE,
}

enum class TextSource { ACCESSIBILITY, OCR, MIXED, SAMPLE }
