package com.ridescore.app.domain.log

import com.ridescore.app.domain.receipt.RideReceipt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The completed-rides log: one row per ride that actually finished.
 *
 * A companion to the offer log, and the half that settles arguments. The offer
 * log holds what RideScore predicted; this holds what happened. Put side by
 * side they answer the questions no amount of reasoning can: whether the
 * estimated minutes were right, whether the deduction settings match the real
 * payout, and what an hour of accepted work actually paid.
 */
object RideCsv {

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    val COLUMNS = listOf(
        "logged_at", "date", "time", "hour", "weekday", "app",
        "order_time", "ride_type",
        "trip_km", "trip_min", "pickup_km",
        "customer_fare", "commission", "taxes_and_fees", "total_earning",
        "deducted", "deducted_percent",
        "earning_per_hour", "earning_per_km", "implied_kmph",
    )

    fun header(): String = COLUMNS.joinToString(",")

    fun row(
        receipt: RideReceipt,
        atMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val moment = Instant.ofEpochMilli(atMillis).atZone(zone)
        val kmph = receipt.tripKm?.let { km ->
            receipt.tripMinutes?.takeIf { it > 0.0 }?.let { km / (it / 60.0) }
        }
        val deductedPercent = receipt.customerFare?.takeIf { it > 0.0 }?.let { fare ->
            receipt.deducted?.let { it / fare * 100.0 }
        }

        return listOf(
            STAMP.format(moment),
            DATE.format(moment),
            TIME.format(moment),
            moment.hour.toString(),
            moment.dayOfWeek.toString().lowercase().replaceFirstChar { it.uppercase() },
            receipt.sourceApp.name,
            receipt.orderTime.orEmpty(),
            receipt.rideType.orEmpty(),
            num(receipt.tripKm),
            num(receipt.tripMinutes),
            num(receipt.pickupKm),
            num(receipt.customerFare),
            num(receipt.commission),
            num(receipt.taxesAndFees),
            num(receipt.totalEarning),
            num(receipt.deducted),
            num(deductedPercent),
            num(receipt.earningPerHour),
            num(receipt.earningPerKm),
            num(kmph),
        ).joinToString(",") { OfferCsv.escape(it) }
    }

    private fun num(value: Double?, decimals: Int = 2): String {
        if (value == null) return ""
        return String.format(java.util.Locale.US, "%.${decimals}f", value)
    }
}
