package com.ridescore.app.parser

import com.ridescore.app.domain.model.ScreenSnapshot
import com.ridescore.app.domain.model.TextBlock

/**
 * Decides how many offers are on the screen.
 *
 * Rapido regularly stacks two or three offers at once. When the accessibility
 * layer has already isolated the offer cards from the view tree we trust it;
 * otherwise we split a flat list of lines by looking for the start of a second
 * fare after a first fare and distance have already been seen.
 */
object OfferSegmenter {

    fun blocks(snapshot: ScreenSnapshot): List<TextBlock> {
        val provided = snapshot.blocks.filter { !it.isEmpty }
        val ocr = snapshot.textSource == com.ridescore.app.domain.model.TextSource.OCR
        val candidates = when {
            // The view tree already isolated separate cards - keep them as-is.
            provided.size > 1 -> provided
            provided.size == 1 -> split(provided.first().lines, ocr)
            else -> split(snapshot.allLines, ocr)
        }
        return candidates.filter { looksLikeOffer(it.lines, ocr) }
    }

    /** Splits a flat list of screen lines into candidate offer cards. */
    fun split(lines: List<String>, ocr: Boolean = false): List<TextBlock> {
        val out = mutableListOf<TextBlock>()
        var current = mutableListOf<String>()
        var hasMoney = false
        var hasDistance = false

        fun flush() {
            if (current.isNotEmpty()) out += TextBlock(current.toList())
            current = mutableListOf()
            hasMoney = false
            hasDistance = false
        }

        for (raw in lines) {
            val n = TextNormalizer.normalize(raw, ocr)
            if (n.isEmpty()) continue

            val money = Extractors.hasCurrency(n) && Extractors.amounts(n).isNotEmpty()
            val distance = Extractors.distancesKm(n).isNotEmpty()
            val isFollowUpAmount =
                Keywords.containsAny(n, Keywords.TOTAL) || Keywords.containsAny(n, Keywords.BONUS)

            // A new fare line, after this card already had a fare and a distance,
            // is the start of the next offer.
            if (money && hasMoney && hasDistance && !isFollowUpAmount && current.size >= 2) {
                flush()
            }

            current += raw
            if (money) hasMoney = true
            if (distance) hasDistance = true

            // Accept/decline buttons close a card. RideScore only reads them.
            if (isActionLine(n) && hasMoney) flush()
        }
        flush()
        return out
    }

    private fun isActionLine(normalized: String): Boolean =
        normalized.length <= 24 && Keywords.containsAny(normalized, Keywords.ACTION_WORDS)

    /**
     * A block is worth parsing if it has a fare, or enough of a journey that the
     * missing fare is worth telling the driver about.
     */
    fun looksLikeOffer(lines: List<String>, ocr: Boolean = false): Boolean {
        var money = false
        var distance = false
        var duration = false
        for (raw in lines) {
            val n = TextNormalizer.normalize(raw, ocr)
            if (Extractors.amounts(n).isNotEmpty()) money = true
            if (Extractors.distancesKm(n).isNotEmpty()) distance = true
            if (Extractors.durationsMinutes(n).isNotEmpty()) duration = true
        }
        return money || (distance && duration)
    }
}
