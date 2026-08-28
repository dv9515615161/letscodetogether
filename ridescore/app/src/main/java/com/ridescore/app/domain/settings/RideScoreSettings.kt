package com.ridescore.app.domain.settings

import com.ridescore.app.domain.model.SourceApp

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

    // ---- Optional costs (off by default) --------------------------------
    val maintenanceEnabled: Boolean = false,
    val maintenancePerKm: Double = DEFAULT_MAINTENANCE_PER_KM,
    val platformFeeEnabled: Boolean = false,
    val platformFeePercent: Double = DEFAULT_PLATFORM_FEE_PERCENT,

    // ---- Output ---------------------------------------------------------
    val overlayEnabled: Boolean = true,
    val overlayMode: OverlayMode = OverlayMode.QUICK,
    val overlayShowDetailsInQuickMode: Boolean = true,
    val voiceEnabled: Boolean = false,
    val voiceMinIntervalMillis: Long = 3_000L,
    val overlayAutoHideMillis: Long = 20_000L,

    // ---- Sources --------------------------------------------------------
    val appMode: AppMode = AppMode.BOTH,
    /**
     * OCR is off until the driver turns it on, because it needs an explicit
     * screen-capture consent from Android. Accessibility text is tried first,
     * always.
     */
    val ocrFallbackEnabled: Boolean = false,

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
        maintenancePerKm = maintenancePerKm.coerceIn(0.0, 100.0),
        platformFeePercent = platformFeePercent.coerceIn(0.0, 90.0),
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
        const val DEFAULT_PLATFORM_FEE_PERCENT = 10.0

        val MILEAGE_PRESETS = listOf(35.0, 36.0, 37.0, 37.5, 38.0, 39.0, 40.0)

        val DEFAULT = RideScoreSettings()
    }
}
