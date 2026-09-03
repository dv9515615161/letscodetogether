package com.ridescore.app.domain.settings

import com.ridescore.app.domain.model.SourceApp

/**
 * How the platform takes its cut.
 *
 * Rapido offers captains a choice, and the two are completely different
 * arithmetic. The fare on an offer card is what the *customer* pays, so on the
 * commission plan a good slice of it never reaches the driver.
 */
enum class EarningsPlan {
    /** A percentage of every fare, plus GST on that percentage. */
    COMMISSION,

    /** A fixed fee per day or week, and then the fare is the driver's. */
    SUBSCRIPTION;

    val label: String
        get() = when (this) {
            COMMISSION -> "Commission"
            SUBSCRIPTION -> "Earnings plan"
        }
}

/** Which driver apps RideScore should watch. */
enum class AppMode { RAPIDO_ONLY, UBER_ONLY, BOTH;

    fun includes(app: SourceApp): Boolean = when (this) {
        RAPIDO_ONLY -> app == SourceApp.RAPIDO
        UBER_ONLY -> app == SourceApp.UBER
        BOTH -> app == SourceApp.RAPIDO || app == SourceApp.UBER
    }

    val label: String
        get() = when (this) {
            RAPIDO_ONLY -> "Rapido only"
            UBER_ONLY -> "Uber only"
            BOTH -> "Both"
        }
}

/** How much of the calculation the overlay shows. */
enum class OverlayMode { QUICK, DETAILED;

    val label: String get() = if (this == QUICK) "Quick" else "Detailed"
}

/**
 * Every user-configurable value in one immutable snapshot.
 *
 * Defaults are the ones in the product brief: Bajaj Pulsar 150 at 37.5 km/L,
 * petrol at Rs.120/L, accept at Rs.150 net/hour, maybe from Rs.120, and a
 * Rs.9/km net floor.
 */
data class RideScoreSettings(
    // ---- Vehicle & fuel -------------------------------------------------
    val vehicleName: String = DEFAULT_VEHICLE,
    val mileageKmPerLitre: Double = DEFAULT_MILEAGE,
    val petrolPricePerLitre: Double = DEFAULT_PETROL_PRICE,

    // ---- Acceptance rules ----------------------------------------------
    val acceptNetPerHour: Double = DEFAULT_ACCEPT_NET_PER_HOUR,
    val maybeNetPerHour: Double = DEFAULT_MAYBE_NET_PER_HOUR,
    val minNetPerKm: Double = DEFAULT_MIN_NET_PER_KM,
    /** ACCEPT needs both the hourly and the per-km rule to pass. */
    val requireBothMetrics: Boolean = true,

    // ---- Journey assumptions -------------------------------------------
    val pickupSpeedKmph: Double = DEFAULT_PICKUP_SPEED,
    /**
     * How fast the paid leg moves, used only when an offer does not print its
     * duration. The anchor for a whole family of speeds - see [tripSpeedFor].
     *
     * Faster than the pickup speed on purpose: riding to a pickup means
     * hunting for a gate and a customer, while the trip itself is a run.
     * Measured from 172 distinct real offers that stated both the distance and
     * the minutes - median 24 km/h, quartiles 19 and 33.
     */
    val tripSpeedKmph: Double = DEFAULT_TRIP_SPEED,
    /**
     * What the same road is worth in bad traffic, as a fraction of the usual
     * speed. 0.6 of 24 km/h is 14 km/h.
     *
     * Not a guess: in 172 offers that printed both a distance and a duration,
     * the driver's median speed was 30.2 km/h at 07:00 and **14.2 at 09:00**.
     * The morning peak is a little over half the early-morning speed, and the
     * slowest tenth of all offers sat at 14.0. So this is what the same ride
     * costs in time when the roads are full.
     */
    val slowTrafficFactor: Double = DEFAULT_SLOW_TRAFFIC_FACTOR,
    /**
     * The anchor speed measured from the road right now, when one is known.
     *
     * Not a stored preference - it is filled in for each analysis from what
     * the apps themselves have been printing, and overrides [tripSpeedKmph]
     * when present. See `domain/speed/SpeedProfile`.
     */
    val liveTripSpeedKmph: Double? = null,
    /** Whether to learn road speed from offers that print their own duration. */
    val learnRoadSpeed: Boolean = true,
    /**
     * Whether ACCEPT must survive the slow-traffic case.
     *
     * When an offer prints no duration RideScore estimates one, and an
     * estimate made at the usual speed is a promise the traffic may not keep:
     * at 09:00 a 12 km trip takes 51 minutes, not the 32 the average predicts,
     * and an offer sold as Rs.150/hr pays Rs.94. With this on, ACCEPT is shown
     * only when the offer still clears the bar at [slowTrafficFactor] speed.
     * Anything that clears only on a good run is a MAYBE, which is the honest
     * answer: it might be worth it, and it depends on the road.
     *
     * Only ever applies to an *estimated* time. A duration printed by the app
     * is taken as read and never stress-tested.
     */
    val requireAcceptToSurviveTraffic: Boolean = true,

    val includePickupDistance: Boolean = true,
    val includePickupTime: Boolean = true,

    // ---- Incentive / quest ----------------------------------------------
    /**
     * Both apps run trip-count bonuses - "12 trips today for ₹300". While one
     * is running, an offer is not worth only its fare: it is worth its fare
     * plus its share of the bonus, and that share grows as the target gets
     * closer. Two trips from a ₹300 bonus, a ₹40 order is really a ₹190 order.
     *
     * The trip count is kept by hand. RideScore has no way to know a ride was
     * completed - it reads offer screens, and it is not going to guess.
     */
    val incentiveEnabled: Boolean = false,
    val incentiveBonus: Double = 0.0,
    val incentiveTripsTarget: Int = 0,
    val incentiveTripsDone: Int = 0,

    // ---- The ride back --------------------------------------------------
    /**
     * A long drop leaves the driver somewhere that may have no order back. The
     * ride home is then unpaid fuel and unpaid time, and it can turn a green
     * offer into a bad hour. Off by default, because whether it applies depends
     * on the city and the hour - but it is the single most useful thing to turn
     * on if long trips have burned you.
     */
    val emptyReturnEnabled: Boolean = false,
    /** Only trips at least this long are assumed to need a ride back. */
    val emptyReturnFromKm: Double = 10.0,
    /** How much of the drop distance you expect to ride back empty. */
    val emptyReturnFraction: Double = 1.0,

    // ---- Optional costs (off by default) --------------------------------
    val maintenanceEnabled: Boolean = false,
    val maintenancePerKm: Double = DEFAULT_MAINTENANCE_PER_KM,
    /**
     * Which plan the driver is on.
     *
     * Defaults to the earnings plan, whose per-order deduction is nothing, so
     * RideScore never invents a cut the driver did not tell it about. The
     * first-run setup asks, because on the commission plan every figure in the
     * app is roughly a fifth too high until it is set.
     */
    val earningsPlan: EarningsPlan = EarningsPlan.SUBSCRIPTION,

    /** Commission plan: the platform's percentage of the customer's fare. */
    val commissionPercent: Double = DEFAULT_COMMISSION_PERCENT,

    /**
     * Commission plan: how much of the fare carries no commission.
     *
     * On three order details from the commission plan the commission line is
     * not 16% of the customer fare, despite saying so. It is 16% of the fare
     * less Rs. 2.50, every time and to the paisa: Rs. 40 was charged Rs. 6.00
     * where 16% is Rs. 6.40, Rs. 82 was charged Rs. 12.72 where 16% is
     * Rs. 13.12, Rs. 60 was charged Rs. 9.20 where 16% is Rs. 9.60. A constant
     * Rs. 0.40 short each time is 16% of Rs. 2.50 - some fixed part of the
     * fare that commission is not charged on.
     */
    val commissionExemptAmount: Double = DEFAULT_COMMISSION_EXEMPT,

    /**
     * Commission plan: GST charged on the commission itself, not on the fare.
     *
     * **Zero by default, because Rapido does not show it here.** The order
     * details put GST inside "Government Taxes and Other Fees", which is the
     * next field down - the commission line is the bare 16%, with no GST added
     * to it. A driver who reads their taxes line off a payout screen has
     * already captured the GST, so charging it again here would count it
     * twice. Left configurable for a platform that does bill it separately.
     */
    val gstOnCommissionPercent: Double = DEFAULT_GST_ON_COMMISSION_PERCENT,

    /**
     * Taxes and fees taken from every order **on either plan**.
     *
     * Rapido's order details call this "Government Taxes and Other Fees", and
     * it is deducted even when commission is 0% on the earnings plan - which
     * is exactly the trap: a driver on that plan sees "0% commission" and
     * assumes the fare is theirs, when about a tenth of it is not.
     */
    val taxesAndFeesPercent: Double = 0.0,

    /**
     * A flat amount kept from every order, whatever its size - the handling
     * fee. Also charged on either plan. A percentage cannot express it: ₹2.82
     * is 5.5% of a ₹51 order and 3.8% of a ₹74 one, so it hurts small orders
     * most.
     */
    val perOrderFee: Double = 0.0,

    /**
     * Whether parcel orders escape the taxes and fees.
     *
     * On the payout screens they do: a parcel order's fare and its earning are
     * the same number, while a bike ride of the same size loses about a tenth.
     * On by default so a driver who enters their ride deductions does not have
     * them wrongly applied to parcel work.
     *
     * Only the taxes and the flat fee are waived. The commission is not: both
     * observed parcel orders were on the plan that charges no commission, so
     * they say nothing about how commission behaves on a parcel, and guessing
     * it away would overstate what the order pays. Overstating is the worse
     * error - it talks a driver into work that is not worth the ride.
     */
    val parcelOrdersExempt: Boolean = true,

    /**
     * Earnings plan: what the plan costs per day.
     *
     * Deliberately *not* subtracted from individual offers. Once the day's fee
     * is paid it is spent whatever the driver does next, so it has no bearing
     * on whether this offer is worth taking - it belongs to the decision about
     * whether to go out at all. RideScore shows it rather than burying it in
     * the rate.
     */
    val dailyPlanFee: Double = 0.0,

    // ---- Output ---------------------------------------------------------
    val overlayEnabled: Boolean = true,
    val overlayMode: OverlayMode = OverlayMode.QUICK,
    val overlayShowDetailsInQuickMode: Boolean = true,
    /** Card text size. Bigger is easier to read at a glance on a bike. */
    val overlayTextScale: Float = 1.0f,
    val voiceEnabled: Boolean = false,
    val voiceMinIntervalMillis: Long = 3_000L,
    val overlayAutoHideMillis: Long = 20_000L,
    /**
     * Show the verdict as a notification when the card cannot be drawn - which
     * on the lock screen is always, for every app.
     */
    val lockScreenNoticeEnabled: Boolean = true,

    // ---- Sources --------------------------------------------------------
    val appMode: AppMode = AppMode.BOTH,

    /**
     * The master switch: whether RideScore is working at all.
     *
     * When off it reads nothing. Not "reads and discards" - [watches] is the
     * one gate every read passes through, so with this false the accessibility
     * service returns before it ever asks Android for the window's contents.
     * The screen is not looked at.
     *
     * It exists because a driver is not always driving. The app has a standing
     * permission to read two apps' screens, and the honest thing is to make
     * withdrawing that a single tap rather than a trip through Android's
     * accessibility settings. There is a Quick Settings tile for exactly this,
     * so it can be flipped from the notification shade without unlocking into
     * the app.
     */
    val onDuty: Boolean = true,
    /**
     * OCR is off until the driver turns it on, because it needs an explicit
     * screen-capture consent from Android. Accessibility text is tried first,
     * always.
     */
    val ocrFallbackEnabled: Boolean = false,

    /**
     * Keep a local CSV of every offer seen, for looking at patterns later.
     * Off by default: it is the only thing RideScore writes to disk.
     */
    val offerLogEnabled: Boolean = false,
    /**
     * Keep a local record of rides that actually finished, read off the
     * order-details screen.
     *
     * Separate from the offer log and separately opt-in, because it records
     * something different: not what the app advised, but what happened. It is
     * the only way to check the advice was any good.
     */
    val rideLogEnabled: Boolean = false,

    // ---- Onboarding ------------------------------------------------------
    /**
     * Whether the driver has read and accepted the disclosure explaining what
     * the accessibility service reads and what it does with it.
     *
     * Google Play requires this consent before an app may ask for accessibility
     * access, and it is the right thing to show regardless: the driver is about
     * to grant a permission that can read screens, and should be told plainly
     * what will and will not be done with that.
     */
    val disclosureAccepted: Boolean = false,

    /**
     * Whether the driver has confirmed their own bike and petrol price.
     *
     * The defaults are a Pulsar 150 at 37.5 km/L. On another bike - an Activa
     * does over 50 - every rupee figure in the app is wrong until this is set,
     * and wrong quietly, which is the worst way to be wrong. So the app asks
     * once, before it is used.
     */
    val setupCompleted: Boolean = false,

    // ---- Confidence -----------------------------------------------------
    /** Below this, a result is never shown as ACCEPT. */
    val lowConfidenceThreshold: Float = 0.75f,
    /** Below this, the card shows CHECK instead of a recommendation. */
    val minUsableConfidence: Float = 0.5f,

    // ---- Destination preferences (display only, v1) ----------------------
    /**
     * Areas the driver likes ending in. v1 only highlights a match; it never
     * claims demand is high, because the app has no demand data.
     */
    val preferredDestinations: List<String> = emptyList(),
) {
    /** Trips still needed for the bonus. Zero once it is earned. */
    val incentiveTripsRemaining: Int
        get() = if (!incentiveEnabled) 0 else (incentiveTripsTarget - incentiveTripsDone).coerceAtLeast(0)

    /**
     * What the next trip is worth in bonus terms.
     *
     * The whole remaining bonus divided by the trips still needed, because
     * every one of those trips is equally required to unlock it. It rises as
     * the target approaches, which is exactly how the decision should change.
     */
    val incentivePerTrip: Double
        get() {
            val remaining = incentiveTripsRemaining
            return if (remaining > 0 && incentiveBonus > 0.0) incentiveBonus / remaining else 0.0
        }

    /**
     * What the platform actually keeps from a fare, as a percentage.
     *
     * On the commission plan that is the commission plus GST charged on the
     * commission: 16% with 18% GST on it is 18.88% of the fare.
     */
    val effectiveCommissionPercent: Double
        get() = if (earningsPlan == EarningsPlan.COMMISSION) {
            commissionPercent * (1.0 + gstOnCommissionPercent / 100.0)
        } else {
            0.0
        }

    /**
     * The commission on a fare of this size, in rupees.
     *
     * Charged on the fare above [commissionExemptAmount], not on all of it.
     * The exempt part matters most where the driver can least afford it: on a
     * Rs. 40 order it is a sixteenth of the commission, on a Rs. 200 order a
     * three-hundredth.
     */
    fun commissionOn(fare: Double): Double =
        ((fare - commissionExemptAmount).coerceAtLeast(0.0) *
            effectiveCommissionPercent / 100.0)

    /**
     * Everything taken as a percentage of the fare: the commission where one
     * applies, plus taxes and fees, which apply either way.
     *
     * An approximation for display only - it ignores both the flat fee and the
     * commission-exempt amount, so it reads slightly high on small orders.
     * [deductionOn] is what the money is actually computed with.
     */
    val totalDeductionPercent: Double
        get() = effectiveCommissionPercent + taxesAndFeesPercent

    /**
     * What the platform keeps from a fare of this size, in rupees.
     *
     * @param isParcel parcel and delivery jobs, which pay no taxes and no flat
     *   fee. Commission, where the plan charges one, still applies.
     */
    fun deductionOn(fare: Double, isParcel: Boolean = false): Double {
        val commission = commissionOn(fare)
        val taxesAndFees = if (isParcel && parcelOrdersExempt) {
            0.0
        } else {
            fare * taxesAndFeesPercent / 100.0 + perOrderFee
        }
        return (commission + taxesAndFees).coerceIn(0.0, fare)
    }

    /** What a parcel order escapes, in rupees, on a fare of this size. */
    fun parcelSavingOn(fare: Double): Double =
        (fare * taxesAndFeesPercent / 100.0 + perOrderFee).coerceAtLeast(0.0)

    /**
     * The speed to assume for a trip of this length.
     *
     * A single number cannot describe both ends of the day's work. Measured
     * over 172 offers that printed a distance and a duration:
     *
     * | Trip | Median speed |
     * |---|---|
     * | under 2 km | 14.9 km/h |
     * | 2 to 5 km | 22.0 km/h |
     * | over 5 km | 32.6 km/h |
     *
     * Short hops are slow per km - the traffic lights, the turning into a
     * lane, the last hundred metres looking for a gate - and they are also the
     * majority: 100 of those 172 were under 5 km. Assuming one average speed
     * across all of them understates a short trip's minutes by half.
     *
     * The bands scale off [tripSpeedKmph] so the driver still has one number
     * to turn if their city is faster or slower than this one.
     */
    fun tripSpeedFor(tripKm: Double): Double = anchorTripSpeed * when {
        tripKm < SHORT_TRIP_KM -> SHORT_TRIP_FACTOR
        tripKm < MEDIUM_TRIP_KM -> MEDIUM_TRIP_FACTOR
        else -> LONG_TRIP_FACTOR
    }

    /**
     * The speed all the bands scale from: what the road is doing now if that
     * has been measured, otherwise the driver's configured typical speed.
     */
    val anchorTripSpeed: Double
        get() = liveTripSpeedKmph?.takeIf { learnRoadSpeed && it > 0.0 } ?: tripSpeedKmph

    /** The same trip when the roads are full. */
    fun slowTripSpeedFor(tripKm: Double): Double =
        tripSpeedFor(tripKm) * slowTrafficFactor.coerceIn(0.1, 1.0)

    /** Rs. per km of fuel. 120 / 37.5 = 3.20 */
    val fuelCostPerKm: Double
        get() = if (mileageKmPerLitre > 0.0) petrolPricePerLitre / mileageKmPerLitre else 0.0

    /** Cost of moving one km, including maintenance only when enabled. */
    val runningCostPerKm: Double
        get() = fuelCostPerKm + if (maintenanceEnabled) maintenancePerKm else 0.0

    fun watches(app: SourceApp): Boolean = onDuty && appMode.includes(app)

    fun sanitised(): RideScoreSettings = copy(
        mileageKmPerLitre = mileageKmPerLitre.coerceIn(5.0, 200.0),
        petrolPricePerLitre = petrolPricePerLitre.coerceIn(1.0, 1000.0),
        acceptNetPerHour = acceptNetPerHour.coerceIn(0.0, 10_000.0),
        maybeNetPerHour = maybeNetPerHour.coerceIn(0.0, acceptNetPerHour),
        minNetPerKm = minNetPerKm.coerceIn(0.0, 1_000.0),
        pickupSpeedKmph = pickupSpeedKmph.coerceIn(3.0, 80.0),
        tripSpeedKmph = tripSpeedKmph.coerceIn(3.0, 80.0),
        slowTrafficFactor = slowTrafficFactor.coerceIn(0.2, 1.0),
        incentiveBonus = incentiveBonus.coerceIn(0.0, 100_000.0),
        incentiveTripsTarget = incentiveTripsTarget.coerceIn(0, 200),
        incentiveTripsDone = incentiveTripsDone.coerceIn(0, 200),
        emptyReturnFromKm = emptyReturnFromKm.coerceIn(0.0, 500.0),
        emptyReturnFraction = emptyReturnFraction.coerceIn(0.0, 1.0),
        overlayTextScale = overlayTextScale.coerceIn(0.8f, 2.0f),
        maintenancePerKm = maintenancePerKm.coerceIn(0.0, 100.0),
        commissionPercent = commissionPercent.coerceIn(0.0, 90.0),
        commissionExemptAmount = commissionExemptAmount.coerceIn(0.0, 1_000.0),
        taxesAndFeesPercent = taxesAndFeesPercent.coerceIn(0.0, 50.0),
        gstOnCommissionPercent = gstOnCommissionPercent.coerceIn(0.0, 100.0),
        perOrderFee = perOrderFee.coerceIn(0.0, 1_000.0),
        dailyPlanFee = dailyPlanFee.coerceIn(0.0, 10_000.0),
    )

    companion object {
        const val DEFAULT_VEHICLE = "Bike - Bajaj Pulsar 150"
        const val DEFAULT_MILEAGE = 37.5
        const val DEFAULT_PETROL_PRICE = 120.0
        const val DEFAULT_ACCEPT_NET_PER_HOUR = 150.0
        const val DEFAULT_MAYBE_NET_PER_HOUR = 120.0
        const val DEFAULT_MIN_NET_PER_KM = 9.0
        const val DEFAULT_PICKUP_SPEED = 17.0

        /** Median of 172 real offers that printed both distance and duration. */
        const val DEFAULT_TRIP_SPEED = 24.0

        /** 0.6 x 24 = 14.4 km/h, the 09:00 median and the slowest tenth. */
        const val DEFAULT_SLOW_TRAFFIC_FACTOR = 0.6

        // Trip-length speed bands, as multiples of the anchor speed. 24 km/h
        // becomes 14.9, 22.1 and 31.7 - the three measured medians.
        const val SHORT_TRIP_KM = 2.0
        const val MEDIUM_TRIP_KM = 5.0
        const val SHORT_TRIP_FACTOR = 0.62
        const val MEDIUM_TRIP_FACTOR = 0.92
        const val LONG_TRIP_FACTOR = 1.32

        const val DEFAULT_MAINTENANCE_PER_KM = 1.5
        const val DEFAULT_COMMISSION_PERCENT = 16.0

        /** Rs. 2.50 of every fare carries no commission - fitted, see above. */
        const val DEFAULT_COMMISSION_EXEMPT = 2.5

        /** Zero: Rapido bills GST inside the taxes line, not on commission. */
        const val DEFAULT_GST_ON_COMMISSION_PERCENT = 0.0

        val MILEAGE_PRESETS = listOf(35.0, 36.0, 37.0, 37.5, 38.0, 39.0, 40.0)

        val DEFAULT = RideScoreSettings()
    }
}
