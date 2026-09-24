package com.ridescore.app

import com.ridescore.app.domain.model.RideOffer
import com.ridescore.app.domain.model.ScreenSnapshot
import com.ridescore.app.domain.model.SourceApp
import com.ridescore.app.domain.model.TextSource

/** Shared builders so the tests read like the screens they describe. */
object TestFixtures {

    const val RAPIDO_PKG = "com.rapido.rider"
    const val UBER_PKG = "com.ubercab.driver"

    /** The offer from the product brief: ₹45 + ₹15, 1.8 km pickup, 5.9 km trip. */
    val RAPIDO_OFFER_A = listOf(
        "Bike",
        "₹45 + ₹15",
        "Pickup 1.8 km",
        "Trip 5.9 km",
        "Trip time 12 mins",
    )

    val RAPIDO_OFFER_B = listOf(
        "Bike",
        "₹49 + ₹17",
        "Pickup 1.4 km",
        "Trip 6.5 km",
        "Trip time 17 mins",
    )

    val UBER_OFFER = listOf(
        "Moto",
        "₹128.55",
        "Includes ₹20 promotion",
        "6 mins (2.1 km) away",
        "Kondapur Metro Station",
        "23 mins (9.4 km) trip",
        "Nallagandla",
    )

    fun rapido(
        lines: List<String>,
        blocks: List<List<String>>? = null,
        source: TextSource = TextSource.ACCESSIBILITY,
        capturedAt: Long = 1_000L,
    ) = ScreenSnapshot.of(RAPIDO_PKG, lines, blocks, source, capturedAt)

    fun uber(
        lines: List<String>,
        blocks: List<List<String>>? = null,
        source: TextSource = TextSource.ACCESSIBILITY,
        capturedAt: Long = 1_000L,
    ) = ScreenSnapshot.of(UBER_PKG, lines, blocks, source, capturedAt)

    fun offer(
        totalFare: Double? = 60.0,
        pickupKm: Double? = 1.8,
        tripKm: Double? = 5.9,
        tripMin: Double? = 12.0,
        pickupMin: Double? = null,
        confidence: Float = 0.95f,
        app: SourceApp = SourceApp.RAPIDO,
    ) = RideOffer(
        sourceApp = app,
        baseFare = totalFare,
        totalFare = totalFare,
        pickupDistanceKm = pickupKm,
        tripDistanceKm = tripKm,
        tripTimeMinutes = tripMin,
        pickupTimeMinutes = pickupMin,
        confidence = confidence,
    )
}
