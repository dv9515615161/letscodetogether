package com.ridescore.app.domain.log

import com.ridescore.app.domain.model.RideAnalysis
import com.ridescore.app.domain.model.ScreenAnalysis
import com.ridescore.app.domain.settings.RideScoreSettings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The ride log's file format.
 *
 * One row per offer seen, in ordinary CSV that opens in any spreadsheet. Pure
 * Kotlin so the schema and the escaping are unit-tested rather than discovered
 * to be broken weeks later, with a month of rows already written.
 *
 * Two deliberate choices:
 *
 *  - Every row carries the *settings in force when it was written* - mileage,
 *    petrol price, thresholds. Without them a row stops meaning anything the
 *    moment the driver changes a setting, and a log you cannot interpret later
 *    is not worth writing.
 *  - Date, time, hour and weekday are separate columns as well as a timestamp,
 *    because the questions this log exists to answer are about time of day and
 *    day of week, and nobody should have to write a formula to ask them.
 */
object OfferCsv {

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    val COLUMNS = listOf(
        "logged_at", "date", "time", "hour", "weekday",
        "app", "screen_id", "offers_on_screen", "rank", "decision", "confidence",
        "base_fare", "bonus_fare", "total_fare", "incentive_share", "total_earning",
        "pickup_km", "trip_km", "total_km",
        "pickup_min", "pickup_min_estimated", "trip_min", "total_min",
        "return_km", "return_min",
        "fuel_cost", "maintenance_cost", "platform_fee", "net_earning",
        "gross_per_hour", "net_per_hour", "gross_per_km", "net_per_km",
        "ride_type", "pickup_location", "destination",
        "mileage_kmpl", "petrol_price", "accept_per_hour", "min_net_per_km",
        "read_from",
    )

    fun header(): String = COLUMNS.joinToString(",")

    /** One row per offer on the screen, best first. */
    fun rows(
        analysis: ScreenAnalysis,
        settings: RideScoreSettings,
        screenId: String,
        atMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<String> = analysis.ranked.mapIndexed { index, ride ->
        row(ride, analysis, settings, screenId, index + 1, atMillis, zone)
    }

    private fun row(
        ride: RideAnalysis,
        screen: ScreenAnalysis,
        settings: RideScoreSettings,
        screenId: String,
        rank: Int,
        atMillis: Long,
        zone: ZoneId,
    ): String {
        val moment = Instant.ofEpochMilli(atMillis).atZone(zone)
        val offer = ride.offer

        return listOf(
            STAMP.format(moment),
            DATE.format(moment),
            TIME.format(moment),
            moment.hour.toString(),
            moment.dayOfWeek.toString().lowercase().replaceFirstChar { it.uppercase() },
            screen.sourceApp.name,
            screenId,
            screen.ranked.size.toString(),
            rank.toString(),
            ride.decision.name,
            num(ride.confidence.toDouble(), 2),
            num(offer.baseFare),
            num(offer.bonusFare),
            num(offer.totalFare),
            num(ride.incentiveEarning),
            num(ride.totalEarning),
            num(offer.pickupDistanceKm),
            num(offer.tripDistanceKm),
            num(ride.totalDistanceKm),
            num(if (ride.pickupTimeEstimated) null else offer.pickupTimeMinutes),
            ride.pickupTimeEstimated.toString(),
            num(offer.tripTimeMinutes),
            num(ride.totalTimeMinutes),
            num(ride.returnDistanceKm),
            num(ride.returnTimeMinutes),
            num(ride.fuelCost),
            num(ride.maintenanceCost),
            num(ride.platformFee),
            num(ride.netEarning),
            num(ride.grossPerHour),
            num(ride.netPerHour),
            num(ride.grossPerKm),
            num(ride.netPerKm),
            offer.rideType.orEmpty(),
            offer.pickupLocation.orEmpty(),
            offer.destination.orEmpty(),
            num(settings.mileageKmPerLitre),
            num(settings.petrolPricePerLitre),
            num(settings.acceptNetPerHour),
            num(settings.minNetPerKm),
            offer.textSource.name,
        ).joinToString(",") { escape(it) }
    }

    /**
     * Addresses on these screens are full of commas - "Kukatpally - 24-230,
     * Balanagar, 500072" - so quoting is not optional.
     */
    fun escape(value: String): String {
        if (value.isEmpty()) return ""
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return value
        return '"' + value.replace("\"", "\"\"").replace('\n', ' ').replace('\r', ' ') + '"'
    }

    private fun num(value: Double?, decimals: Int = 2): String {
        if (value == null) return ""
        return String.format(java.util.Locale.US, "%.${decimals}f", value)
    }
}
