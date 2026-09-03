package com.ridescore.app.calculator

import com.ridescore.app.decision.DecisionEngine
import com.ridescore.app.decision.DecisionInput
import com.ridescore.app.domain.model.RideAnalysis
import com.ridescore.app.domain.model.RideOffer
import com.ridescore.app.domain.settings.RideScoreSettings
import kotlin.math.ceil

/**
 * Turns a parsed offer into money per hour and money per km.
 *
 * The whole calculation, in the order it runs:
 * ```
 * fuelCostPerKm  = petrolPrice / mileage            120 / 37.5 = 3.20
 * totalDistance  = pickupKm + tripKm                1.8 + 5.9  = 7.7 km
 * pickupMinutes  = ceil(pickupKm / pickupSpeed*60)  1.8 @ 17kmph = 7 min
 * totalTime      = pickupMinutes + tripMinutes      7 + 12     = 19 min
 * gross          = totalFare                        45 + 15    = ₹60
 * fuelCost       = totalDistance * fuelCostPerKm    7.7 * 3.20 = ₹24.64
 * net            = gross - fuel - (optional costs)  60 - 24.64 = ₹35.36
 * netPerHour     = net / (totalTime / 60)           35.36/0.3167 = ₹111.66
 * netPerKm       = net / totalDistance              35.36/7.7  = ₹4.59
 * ```
 *
 * Maintenance and platform fee are subtracted **only** when the driver has
 * turned them on. By default "net" means "after fuel", nothing more, and the
 * UI labels it that way.
 */
class FareCalculator(
    private val decisionEngine: DecisionEngine = DecisionEngine(),
) {

    fun analyse(offer: RideOffer, settings: RideScoreSettings): RideAnalysis {
        val s = settings.sanitised()
        val notes = mutableListOf<String>()

        val tripKm = offer.tripDistanceKm ?: 0.0
        val pickupKm = offer.pickupDistanceKm ?: 0.0
        val countedPickupKm = if (s.includePickupDistance) pickupKm else 0.0
        val totalKm = tripKm + countedPickupKm

        // A missing trip time used to count as zero minutes, which made a fare
        // divided by the pickup leg alone: 12.6 km for Rs.126 came out at
        // Rs.4365 an hour and was offered as a MAYBE. Estimating the leg from
        // its distance is the same bargain already struck for the pickup leg -
        // an honest approximation, marked as one - and it is the difference
        // between calling that ride a MAYBE and calling it the reject it is.
        val tripTimeEstimated = offer.tripTimeMinutes == null && tripKm > 0.0
        val tripMin = offer.tripTimeMinutes
            ?: estimateRidingMinutes(tripKm, s.tripSpeedFor(tripKm))
        // The same trip when the roads are full. Only differs from tripMin
        // when the duration was estimated - a printed one is taken as read.
        val slowTripMin =
            if (tripTimeEstimated) estimateRidingMinutes(tripKm, s.slowTripSpeedFor(tripKm))
            else tripMin
        val pickupTimeEstimated = offer.pickupTimeMinutes == null && pickupKm > 0.0
        val pickupMin = offer.pickupTimeMinutes
            ?: estimateRidingMinutes(pickupKm, s.pickupSpeedKmph)
        val countedPickupMin = if (s.includePickupTime) pickupMin else 0.0
        val totalMin = tripMin + countedPickupMin
        val slowTotalMin = slowTripMin + countedPickupMin

        if (tripTimeEstimated) {
            notes += "Trip time estimated at ${fmtMinutes(tripMin)} min " +
                "(${fmtKmph(s.tripSpeedFor(tripKm))} km/h) - the offer did not show it"
            if (slowTripMin > tripMin) {
                notes += "In heavy traffic nearer ${fmtMinutes(slowTripMin)} min"
            }
        }
        if (pickupTimeEstimated && s.includePickupTime) {
            notes += "Pickup time estimated at ${fmtMinutes(pickupMin)} min (${fmtKmph(s.pickupSpeedKmph)} km/h)"
        }
        if (!s.includePickupDistance && pickupKm > 0.0) {
            notes += "Pickup distance excluded by settings"
        }

        // The ride back. A 30 km drop can pay well for the drop and still ruin
        // the hour, because the kilometres home are unpaid fuel and unpaid
        // time. Nothing is assumed about demand out there - only that if no
        // order comes, the driver rides back at the speed they just rode out.
        val returnKm =
            if (s.emptyReturnEnabled && tripKm >= s.emptyReturnFromKm) tripKm * s.emptyReturnFraction
            else 0.0
        val returnMin = if (returnKm > 0.0) {
            val tripSpeed = if (tripMin > 0.0) tripKm / (tripMin / 60.0) else s.pickupSpeedKmph
            if (tripSpeed > 0.0) returnKm / tripSpeed * 60.0 else 0.0
        } else {
            0.0
        }
        if (returnKm > 0.0) {
            notes += "Includes ${fmtMinutes(returnKm)} km ridden back empty"
        }

        // Distance that costs fuel, and time that is spent, both include the
        // ride back. Only the paid leg earns.
        val costedKm = totalKm + returnKm
        val spentMin = totalMin + returnMin
        val slowSpentMin = slowTotalMin + returnMin

        val gross = offer.totalFare ?: 0.0

        // A trip-count bonus makes every remaining trip worth more than its
        // fare. Only counted when the offer is one RideScore could actually
        // read - a share of a bonus attached to an unreadable offer would just
        // be a made-up number.
        val incentive = if (gross > 0.0) s.incentivePerTrip else 0.0
        val earned = gross + incentive

        // The fare on an offer card is what the customer pays, and a slice of
        // it never reaches the driver: commission where a plan charges one,
        // plus taxes and fees, which are taken on either plan. Charged on the
        // fare, not on a bonus.
        val platformFee = s.deductionOn(gross, offer.looksLikeParcel)
        if (offer.looksLikeParcel && s.parcelOrdersExempt && s.parcelSavingOn(gross) > 0.0) {
            // Parcels pay no tax and no flat fee. Commission, where the plan
            // charges one, is still taken - so this note says tax, not "no
            // deduction".
            notes += "Parcel order: no tax or fee deducted"
        }
        val fuelCost = costedKm * s.fuelCostPerKm
        val maintenanceCost = if (s.maintenanceEnabled) costedKm * s.maintenancePerKm else 0.0
        val net = earned - platformFee - fuelCost - maintenanceCost

        if (incentive > 0.0) {
            notes += "Includes ₹${incentive.toInt()} of the ₹${s.incentiveBonus.toInt()} bonus, " +
                "${s.incentiveTripsRemaining} trip(s) to go"
        }

        val hours = spentMin / 60.0
        val grossPerHour = if (hours > 0.0) earned / hours else 0.0
        val netPerHour = if (hours > 0.0) net / hours else 0.0
        // Same money, more minutes. The distance does not change, so neither
        // does the fuel - only the hourly rate suffers.
        val slowHours = slowSpentMin / 60.0
        val netPerHourInTraffic = if (slowHours > 0.0) net / slowHours else 0.0
        val grossPerKm = if (costedKm > 0.0) earned / costedKm else 0.0
        val netPerKm = if (costedKm > 0.0) net / costedKm else 0.0

        val outcome = decisionEngine.decide(
            DecisionInput(
                offer = offer,
                totalDistanceKm = costedKm,
                totalTimeMinutes = spentMin,
                netPerHour = netPerHour,
                netPerKm = netPerKm,
                confidence = offer.confidence,
                netPerHourInTraffic = netPerHourInTraffic,
                timeEstimated = tripTimeEstimated,
            ),
            s,
        )

        return RideAnalysis(
            offer = offer,
            totalDistanceKm = costedKm,
            totalTimeMinutes = spentMin,
            grossEarning = gross,
            incentiveEarning = incentive,
            fuelCost = fuelCost,
            maintenanceCost = maintenanceCost,
            platformFee = platformFee,
            netEarning = net,
            grossPerHour = grossPerHour,
            netPerHour = netPerHour,
            grossPerKm = grossPerKm,
            netPerKm = netPerKm,
            decision = outcome.decision,
            confidence = offer.confidence,
            reasons = outcome.reasons,
            notes = offer.notes + notes,
            pickupTimeEstimated = pickupTimeEstimated,
            tripTimeEstimated = tripTimeEstimated,
            tripTimeMinutesCounted = tripMin,
            netPerHourInTraffic = netPerHourInTraffic,
            totalTimeMinutesInTraffic = slowSpentMin,
            pickupTimeMinutesCounted = countedPickupMin,
            returnDistanceKm = returnKm,
            returnTimeMinutes = returnMin,
        )
    }

    companion object {
        /**
         * Riding minutes to the pickup point, rounded up to the whole minute
         * the driver would actually see: 1.8 km at 17 km/h is 6.35 minutes,
         * shown and counted as 7.
         */
        fun estimateRidingMinutes(km: Double, speedKmph: Double): Double {
            if (km <= 0.0 || speedKmph <= 0.0) return 0.0
            return ceil(km / speedKmph * 60.0)
        }

        /** Kept as the old name for the pickup leg, which reads better there. */
        fun estimatePickupMinutes(pickupKm: Double, pickupSpeedKmph: Double): Double =
            estimateRidingMinutes(pickupKm, pickupSpeedKmph)

        private fun fmtMinutes(v: Double): String =
            if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)

        private fun fmtKmph(v: Double): String =
            if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
    }
}
