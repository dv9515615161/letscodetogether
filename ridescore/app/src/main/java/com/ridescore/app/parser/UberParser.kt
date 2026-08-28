package com.ridescore.app.parser

import com.ridescore.app.domain.model.ScreenSnapshot
import com.ridescore.app.domain.model.SourceApp
import com.ridescore.app.domain.model.TextSource

/**
 * Uber Driver.
 *
 * Typical offer card:
 * ```
 * Moto
 * ₹128.55
 * Includes ₹20 promotion
 * 6 mins (2.1 km) away
 * Kondapur Metro Station
 * 23 mins (9.4 km) trip
 * Nallagandla
 * ```
 * Two Uber-specific things: the legs are written as "N mins (D km) away/trip",
 * and a promotion is usually *already inside* the headline amount, so it must
 * not be added on top of it. [BaseOfferParser.resolveFare] handles both once
 * the vocabulary below labels the lines correctly.
 */
class UberParser : BaseOfferParser(SourceApp.UBER) {

    override val pickupWords: List<String> = Keywords.PICKUP + listOf(
        "away", "to rider", "to pickup", "pickup in",
    )

    override val dropWords: List<String> = Keywords.DROP + listOf(
        "trip", "to destination", "dropoff", "drop off",
    )

    override val totalWords: List<String> = Keywords.TOTAL + listOf(
        "you earn", "fare", "upfront",
    )

    override val bonusWords: List<String> = Keywords.BONUS + listOf(
        "promotion", "promo", "surge", "quest", "consecutive",
    )

    override fun canParse(snapshot: ScreenSnapshot): Boolean {
        if (snapshot.sourceApp != SourceApp.UBER || snapshot.isEmpty) return false
        val ocr = snapshot.textSource == TextSource.OCR
        return OfferSegmenter.looksLikeOffer(snapshot.allLines, ocr)
    }
}
