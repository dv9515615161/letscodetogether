package com.ridescore.app.ui

import com.ridescore.app.domain.model.ScreenSnapshot
import com.ridescore.app.domain.model.TextSource

/**
 * The offers from the product brief, used by the "try it" button so a driver
 * can see what the card looks like before their first real ride offer.
 */
object SampleOffers {

    val RAPIDO_SINGLE: ScreenSnapshot
        get() = ScreenSnapshot.of(
            packageName = "com.rapido.rider",
            lines = listOf(
                "Bike",
                "₹45 + ₹15",
                "Pickup 1.8 km",
                "Kondapur Metro",
                "Trip 5.9 km",
                "Nallagandla",
                "Trip time 12 mins",
            ),
            textSource = TextSource.SAMPLE,
            capturedAtMillis = System.currentTimeMillis(),
        )

    val RAPIDO_TWO_OFFERS: ScreenSnapshot
        get() = ScreenSnapshot.of(
            packageName = "com.rapido.rider",
            lines = listOf(
                "Bike", "₹45 + ₹15", "Pickup 1.8 km", "Trip 5.9 km", "Trip time 12 mins",
                "Bike", "₹49 + ₹17", "Pickup 1.4 km", "Trip 6.5 km", "Trip time 17 mins",
            ),
            textSource = TextSource.SAMPLE,
            capturedAtMillis = System.currentTimeMillis(),
        )
}
