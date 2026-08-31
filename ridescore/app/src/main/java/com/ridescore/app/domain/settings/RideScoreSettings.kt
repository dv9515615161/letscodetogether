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
     * Commission plan: GST charged on the commission itself, not on the fare.
     * 16% commission with 18% GST on it takes 18.88% of the fare, not 34%.
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
     * Everything taken as a percentage of the fare: the commission where one
     * applies, plus taxes and fees, which apply either way.
     */
    val totalDeductionPercent: Double
        get() = effectiveCommissionPercent + taxesAndFeesPercent

    /**
     * What the platform keeps from a fare of this size, in rupees.
     *
     * @param isParcel parcel and delivery jobs, which are deducted differently
     *   - on Rapido, not at all.
     */
    fun deductionOn(fare: Double, isParcel: Boolean = false): Double {
        if (isParcel && parcelOrdersExempt) return 0.0
        return (fare * totalDeductionPercent / 100.0 + perOrderFee).coerceIn(0.0, fare)
    }

    /** Rs. per km of fuel. 120 / 37.5 = 3.20 */
    val fuelCostPerKm: Double
        get() = if (mileageKmPerLitre > 0.0) petrolPricePerLitre / mileageKmPerLitre else 0.0

    /** Cost of moving one km, including maintenance only when enabled. */
    val runningCostPerKm: Double
        get() = fuelCostPerKm + if (maintenanceEnabled) maintenancePerKm else 0.0

    fun watches(app: SourceApp): Boolean = appMode.includes(app)

    fun sanitised(): RideScoreSettings = copy(
        mileageKmPerLitre = mileageKmPerLitre.coerceIn(5.0, 200.0),
        petrolPricePerLitre = petrolPricePerLitre.coerceIn(1.0, 1000.0),
        acceptNetPerHour = acceptNetPerHour.coerceIn(0.0, 10_000.0),
        maybeNetPerHour = maybeNetPerHour.coerceIn(0.0, acceptNetPerHour),
        minNetPerKm = minNetPerKm.coerceIn(0.0, 1_000.0),
        pickupSpeedKmph = pickupSpeedKmph.coerceIn(3.0, 80.0),
        incentiveBonus = incentiveBonus.coerceIn(0.0, 100_000.0),
        incentiveTripsTarget = incentiveTripsTarget.coerceIn(0, 200),
        incentiveTripsDone = incentiveTripsDone.coerceIn(0, 200),
        emptyReturnFromKm = emptyReturnFromKm.coerceIn(0.0, 500.0),
        emptyReturnFraction = emptyReturnFraction.coerceIn(0.0, 1.0),
        overlayTextScale = overlayTextScale.coerceIn(0.8f, 2.0f),
        maintenancePerKm = maintenancePerKm.coerceIn(0.0, 100.0),
        commissionPercent = commissionPercent.coerceIn(0.0, 90.0),
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
        const val DEFAULT_MAINTENANCE_PER_KM = 1.5
        const val DEFAULT_COMMISSION_PERCENT = 16.0
        const val DEFAULT_GST_ON_COMMISSION_PERCENT = 18.0

        val MILEAGE_PRESETS = listOf(35.0, 36.0, 37.0, 37.5, 38.0, 39.0, 40.0)

        val DEFAULT = RideScoreSettings()
    }
}
