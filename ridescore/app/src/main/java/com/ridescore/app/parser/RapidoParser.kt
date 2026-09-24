package com.ridescore.app.parser

import com.ridescore.app.domain.model.ScreenSnapshot
import com.ridescore.app.domain.model.SourceApp
import com.ridescore.app.domain.model.TextSource

/**
 * Rapido Captain.
 *
 * Typical offer card:
 * ```
 * Bike
 * ₹45 + ₹15
 * Pickup 1.8 km
 * Kondapur Metro
 * Drop 5.9 km
 * Nallagandla
 * Trip time 12 mins
 * ```
 * Rapido is the app most likely to stack two or three offers at once, so the
 * segmenter matters more here than anywhere else.
 */
class RapidoParser : BaseOfferParser(SourceApp.RAPIDO) {

    override val pickupWords: List<String> = Keywords.PICKUP + listOf(
        "pickup dist", "customer pickup", "pickup in",
    )

    override val dropWords: List<String> = Keywords.DROP + listOf(
        "drop dist", "ride dist", "trip dist", "drop point",
    )

    override val totalWords: List<String> = Keywords.TOTAL + listOf(
        "customer pays", "you will get", "captain earning", "your earning",
    )

    override val bonusWords: List<String> = Keywords.BONUS + listOf(
        "rain", "night", "extra fare", "add on", "addon",
    )

    override fun canParse(snapshot: ScreenSnapshot): Boolean {
        if (snapshot.sourceApp != SourceApp.RAPIDO || snapshot.isEmpty) return false
        val ocr = snapshot.textSource == TextSource.OCR
        return OfferSegmenter.looksLikeOffer(snapshot.allLines, ocr)
    }
}
