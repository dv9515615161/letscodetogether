package com.ridescore.app.parser

import com.ridescore.app.domain.model.OfferField
import com.ridescore.app.domain.model.RideOffer
import com.ridescore.app.domain.model.ScreenSnapshot
import com.ridescore.app.domain.model.SourceApp
import com.ridescore.app.domain.model.TextSource

/**
 * Everything the app parsers share.
 *
 * Parsing runs as a set of independent strategies rather than one layout
 * template, because these screens change often:
 *
 *  1. **Labelled lines** - "Pickup 1.8 km", "23 mins (9.4 km) trip".
 *  2. **Sum expressions** - "₹45 + ₹15" gives base and bonus in one go.
 *  3. **Positional fallback** - when nothing is labelled, both apps show the
 *     pickup leg before the trip leg. Used, but it costs confidence.
 *  4. **Reconciliation** - a printed total that disagrees with base + bonus
 *     lowers confidence and is recorded as a note.
 *
 * Anything that cannot be read stays null. The parser never fills a gap with a
 * guessed number.
 */
abstract class BaseOfferParser(override val sourceApp: SourceApp) : RideOfferParser {

    protected open val pickupWords: List<String> = Keywords.PICKUP
    protected open val dropWords: List<String> = Keywords.DROP
    protected open val bonusWords: List<String> = Keywords.BONUS
    protected open val totalWords: List<String> = Keywords.TOTAL
    protected open val rideTypeWords: List<String> = Keywords.RIDE_TYPES

    override fun canParse(snapshot: ScreenSnapshot): Boolean {
        if (snapshot.sourceApp != sourceApp || snapshot.isEmpty) return false
        return OfferSegmenter.looksLikeOffer(snapshot.allLines, snapshot.textSource == TextSource.OCR)
    }

    override fun parse(snapshot: ScreenSnapshot): List<RideOffer> {
        if (snapshot.isEmpty) return emptyList()
        val blocks = OfferSegmenter.blocks(snapshot)
        return blocks.mapNotNull { block ->
            parseBlock(block.lines, snapshot, blocks.size)
        }
    }

    protected open fun parseBlock(
        lines: List<String>,
        snapshot: ScreenSnapshot,
        blockCount: Int = 1,
    ): RideOffer? {
        val ocr = snapshot.textSource == TextSource.OCR
        val infos = lines.mapIndexed { i, raw -> lineInfo(i, raw, ocr) }.filter { it.text.isNotEmpty() }
        if (infos.isEmpty()) return null

        val notes = mutableListOf<String>()
        val fields = mutableMapOf<OfferField, Float>()
        var penalty = 0f

        // ---- fare ---------------------------------------------------------
        var fare = resolveFare(infos, notes)

        // The fare is not always inside the same card as the journey - some
        // layouts put it in a separate view, or a separate window. When there
        // is only one offer on screen there is no ambiguity about who it
        // belongs to, so look for it elsewhere rather than giving up.
        if (fare.total == null && blockCount == 1) {
            rescueFare(infos, snapshot)?.let {
                fare = it
                penalty += RESCUE_PENALTY
                notes += "Fare read from elsewhere on the screen"
            }
        }
        penalty += fare.penalty
        if (fare.total != null) fields[OfferField.TOTAL_FARE] = fare.totalConfidence
        if (fare.base != null) fields[OfferField.BASE_FARE] = fare.totalConfidence
        if (fare.bonus != null) fields[OfferField.BONUS_FARE] = fare.totalConfidence

        // ---- journey ------------------------------------------------------
        var pickupKm: Double? = null
        var tripKm: Double? = null
        var pickupMin: Double? = null
        var tripMin: Double? = null
        val looseKm = mutableListOf<Double>()
        val looseMin = mutableListOf<Double>()

        for (li in infos) {
            when (li.label) {
                Label.PICKUP -> {
                    if (pickupKm == null) pickupKm = li.distances.firstOrNull()
                    if (pickupMin == null) pickupMin = li.durations.firstOrNull()
                }
                Label.DROP -> {
                    if (tripKm == null) tripKm = li.distances.firstOrNull()
                    if (tripMin == null) tripMin = li.durations.firstOrNull()
                }
                Label.NONE -> {
                    looseKm += li.distances
                    looseMin += li.durations
                }
            }
        }
        if (pickupKm != null) fields[OfferField.PICKUP_DISTANCE] = LABELLED
        if (tripKm != null) fields[OfferField.TRIP_DISTANCE] = LABELLED
        if (pickupMin != null) fields[OfferField.PICKUP_TIME] = LABELLED
        if (tripMin != null) fields[OfferField.TRIP_TIME] = LABELLED

        // Positional strategy: both apps put the pickup leg before the trip leg.
        if (pickupKm == null && tripKm == null && looseKm.size >= 2) {
            pickupKm = looseKm[0]
            tripKm = looseKm[1]
            fields[OfferField.PICKUP_DISTANCE] = POSITIONAL
            fields[OfferField.TRIP_DISTANCE] = POSITIONAL
            penalty += POSITIONAL_PENALTY
            notes += "Pickup/trip distance read by position, not by label"
        } else if (tripKm == null && looseKm.isNotEmpty()) {
            tripKm = looseKm.first()
            fields[OfferField.TRIP_DISTANCE] = POSITIONAL
            penalty += POSITIONAL_PENALTY
            notes += "Trip distance read by position, not by label"
        } else if (pickupKm == null && looseKm.isNotEmpty()) {
            pickupKm = looseKm.first()
            fields[OfferField.PICKUP_DISTANCE] = POSITIONAL
            penalty += POSITIONAL_PENALTY
        }

        if (tripMin == null && looseMin.isNotEmpty()) {
            // A single unlabelled duration on an offer card is the trip time.
            tripMin = if (looseMin.size >= 2 && pickupMin == null) {
                pickupMin = looseMin[0]
                fields[OfferField.PICKUP_TIME] = POSITIONAL
                looseMin[1]
            } else {
                looseMin.first()
            }
            fields[OfferField.TRIP_TIME] = POSITIONAL
            penalty += POSITIONAL_PENALTY
            notes += "Trip time read by position, not by label"
        } else if (pickupMin == null && looseMin.size >= 2) {
            pickupMin = looseMin.first()
            fields[OfferField.PICKUP_TIME] = POSITIONAL
        }

        // ---- text fields --------------------------------------------------
        val pickupLocation = locationFor(infos, Label.PICKUP, pickupWords)
        val destination = locationFor(infos, Label.DROP, dropWords)
        val rideType = rideType(infos)
        if (pickupLocation != null) fields[OfferField.PICKUP_LOCATION] = LABELLED
        if (destination != null) fields[OfferField.DESTINATION] = LABELLED
        if (rideType != null) fields[OfferField.RIDE_TYPE] = LABELLED

        // ---- confidence ---------------------------------------------------
        var confidence = 0f
        if (fare.total != null && fare.total > 0.0) confidence += W_FARE
        if (tripKm != null) confidence += W_TRIP_KM
        if (tripMin != null) confidence += W_TRIP_MIN
        if (pickupKm != null) confidence += W_PICKUP_KM
        if (pickupMin != null) confidence += W_PICKUP_MIN
        confidence -= penalty
        if (ocr) {
            confidence *= OCR_FACTOR
            notes += "Read by OCR fallback"
        }
        confidence = confidence.coerceIn(0f, 1f)

        return RideOffer(
            sourceApp = sourceApp,
            baseFare = fare.base,
            bonusFare = fare.bonus,
            totalFare = fare.total,
            pickupDistanceKm = pickupKm,
            tripDistanceKm = tripKm,
            tripTimeMinutes = tripMin,
            pickupTimeMinutes = pickupMin,
            pickupLocation = pickupLocation,
            destination = destination,
            rideType = rideType,
            timestamp = snapshot.capturedAtMillis,
            confidence = confidence,
            fieldConfidence = fields,
            textSource = snapshot.textSource,
            notes = notes,
            rawLines = lines,
        )
    }

    // ---------------------------------------------------------------- fare

    protected data class FareResult(
        val base: Double?,
        val bonus: Double?,
        val total: Double?,
        val penalty: Float,
        val totalConfidence: Float,
    )

    /**
     * Fare strategies, in order of trust:
     *  - `₹45 + ₹15` sum expression,
     *  - an amount on a line labelled total / you earn / customer pays,
     *  - the largest rupee amount that is not on a bonus line,
     *  - a bare number on a fare-labelled line (OCR sometimes drops the ₹).
     */
    protected open fun resolveFare(infos: List<LineInfo>, notes: MutableList<String>): FareResult {
        var penalty = 0f

        val sumLine = infos.firstOrNull { li ->
            (li.hasCurrency || li.isTotal || li.isBonus) && Extractors.sumExpression(li.text) != null
        }
        val sum = sumLine?.let { Extractors.sumExpression(it.text) }

        val bonusLine = infos.firstOrNull { it.isBonus && it.amounts.isNotEmpty() }
        val bonusAmount = bonusLine?.amounts?.firstOrNull()
        val bonusIncluded = bonusLine?.bonusIncluded == true

        val mainAmounts = infos.filter { !it.isBonus }.flatMap { it.amounts }.filter { it > 0.0 }
        val labelledTotal = infos.firstOrNull { it.isTotal && it.amounts.isNotEmpty() }
            ?.amounts?.maxOrNull()

        if (sum != null) {
            val (base, bonus) = sum
            val total = base + bonus
            if (labelledTotal != null && kotlin.math.abs(labelledTotal - total) > 1.0) {
                notes += "Printed total ₹${fmt(labelledTotal)} differs from ₹${fmt(base)} + ₹${fmt(bonus)}"
                penalty += MISMATCH_PENALTY
                return FareResult(labelledTotal - bonus, bonus, labelledTotal, penalty, 0.6f)
            }
            return FareResult(base, bonus, total, penalty, 1.0f)
        }

        val headline = labelledTotal ?: mainAmounts.maxOrNull()
        if (headline == null) {
            // Last resort: a bare number sitting on a fare-labelled line.
            val bare = infos.firstOrNull { it.isTotal }?.let { Extractors.bareNumbers(it.text).maxOrNull() }
            if (bare != null && bare > 0.0) {
                notes += "Fare read without a ₹ symbol"
                return FareResult(bare, bonusAmount, bare, penalty + MISMATCH_PENALTY, 0.5f)
            }
            return FareResult(null, bonusAmount, null, penalty, 0f)
        }

        return when {
            bonusAmount == null -> FareResult(headline, null, headline, penalty, 0.9f)
            bonusIncluded -> {
                notes += "Bonus ₹${fmt(bonusAmount)} already inside ₹${fmt(headline)}"
                FareResult(headline - bonusAmount, bonusAmount, headline, penalty, 0.9f)
            }
            else -> {
                notes += "Bonus ₹${fmt(bonusAmount)} added to ₹${fmt(headline)}"
                FareResult(headline, bonusAmount, headline + bonusAmount, penalty, 0.85f)
            }
        }
    }

    /**
     * Looks for the fare outside the offer card.
     *
     * Only accepts an unmistakable fare - a `₹45 + ₹18` sum, or an amount on a
     * line that names itself as the total - so a wallet balance or a promo
     * banner elsewhere on the screen can never be mistaken for the offer.
     */
    private fun rescueFare(blockInfos: List<LineInfo>, snapshot: ScreenSnapshot): FareResult? {
        val alreadySeen = blockInfos.mapTo(HashSet()) { it.raw }
        val ocr = snapshot.textSource == TextSource.OCR
        val others = snapshot.allLines
            .filterNot { it in alreadySeen }
            .mapIndexed { i, raw -> lineInfo(i, raw, ocr) }
            .filter { it.text.isNotEmpty() }
        if (others.isEmpty()) return null

        val sum = others
            .firstOrNull { (it.hasCurrency || it.isTotal) && Extractors.sumExpression(it.text) != null }
            ?.let { Extractors.sumExpression(it.text) }
        if (sum != null) {
            return FareResult(sum.first, sum.second, sum.first + sum.second, 0f, 0.7f)
        }

        val labelled = others.firstOrNull { it.isTotal && it.amounts.isNotEmpty() }
            ?.amounts?.maxOrNull()
        if (labelled != null && labelled > 0.0) {
            return FareResult(labelled, null, labelled, 0f, 0.6f)
        }
        return null
    }

    // ------------------------------------------------------------ text bits

    /**
     * The address next to a pickup/drop label. Either the rest of the labelled
     * line, or the line right below it when the labelled line carries the
     * distance instead.
     */
    private fun locationFor(infos: List<LineInfo>, label: Label, words: List<String>): String? {
        for ((idx, li) in infos.withIndex()) {
            if (li.label != label) continue

            remainderAfterKeyword(li.raw, words)?.let { if (isPlaceLike(it)) return it }

            val next = infos.getOrNull(idx + 1) ?: continue
            val candidate = next.raw.trim()
            if (next.label == Label.NONE && isPlaceLike(candidate)) return candidate
        }
        return null
    }

    private fun remainderAfterKeyword(raw: String, words: List<String>): String? {
        val lower = raw.lowercase()
        var best = -1
        var len = 0
        for (w in words) {
            val i = lower.indexOf(w)
            if (i >= 0 && (best < 0 || i < best)) { best = i; len = w.length }
        }
        if (best < 0) return null
        return raw.substring(best + len).trim().trimStart(':', '-', '.', ' ', '>', '|')
            .trim().ifEmpty { null }
    }

    /**
     * Whether a line reads like an address rather than a measurement.
     *
     * Digits cannot disqualify it: a real Indian address is full of them -
     * "Kukatpally - 24-230, Kukatpally House Phase 1, Balanagar, 500072" has a
     * house number and a PIN code. What disqualifies a line is carrying a
     * distance, a duration or a fare, or being mostly digits with no words.
     */
    private fun isPlaceLike(text: String): Boolean {
        if (text.length !in 3..100) return false
        if (text.contains('₹')) return false

        val normalized = TextNormalizer.normalize(text)
        if (Extractors.amounts(normalized).isNotEmpty()) return false
        if (Extractors.distancesKm(normalized).isNotEmpty()) return false
        if (Extractors.durationsMinutes(normalized).isNotEmpty()) return false
        if (Keywords.containsAny(normalized, Keywords.ACTION_WORDS)) return false

        val letters = text.count { it.isLetter() }
        return letters >= 3 && letters > text.count { it.isDigit() }
    }

    private fun rideType(infos: List<LineInfo>): String? {
        for (li in infos) {
            if (li.hasDigits || li.text.length > 25) continue
            val tokens = li.text.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) continue
            if (tokens.any { it in rideTypeWords }) return li.raw.trim()
        }
        return null
    }

    // ------------------------------------------------------------ line model

    protected enum class Label { PICKUP, DROP, NONE }

    protected data class LineInfo(
        val index: Int,
        val raw: String,
        val text: String,
        val amounts: List<Double>,
        val distances: List<Double>,
        val durations: List<Double>,
        val label: Label,
        val isBonus: Boolean,
        val isTotal: Boolean,
        val bonusIncluded: Boolean,
        val hasCurrency: Boolean,
    ) {
        val hasDigits: Boolean get() = text.any { it.isDigit() }
    }

    protected fun lineInfo(index: Int, raw: String, ocr: Boolean): LineInfo {
        val text = TextNormalizer.normalize(raw, ocr)
        val pickupAt = Keywords.firstHitIndex(text, pickupWords)
        val dropAt = Keywords.firstHitIndex(text, dropWords)
        val label = when {
            pickupAt < 0 && dropAt < 0 -> Label.NONE
            dropAt < 0 -> Label.PICKUP
            pickupAt < 0 -> Label.DROP
            pickupAt <= dropAt -> Label.PICKUP
            else -> Label.DROP
        }
        return LineInfo(
            index = index,
            raw = raw,
            text = text,
            amounts = Extractors.amounts(text),
            distances = Extractors.distancesKm(text),
            durations = Extractors.durationsMinutes(text),
            label = label,
            isBonus = Keywords.containsAny(text, bonusWords),
            isTotal = Keywords.containsAny(text, totalWords),
            bonusIncluded = Keywords.containsAny(text, Keywords.INCLUDED),
            hasCurrency = Extractors.hasCurrency(text),
        )
    }

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.2f", v)

    companion object {
        /*
         * Confidence weights, adding to 1.0 for a fully readable offer.
         *
         * Weighted by what the money depends on, not by how full the screen
         * is. The fare and the distances decide the answer; the durations are
         * estimated from the distances when an app does not print them, and
         * the card marks an estimate with a tilde. So a missing duration is
         * not evidence of a bad read - on Rapido it is simply the usual
         * layout, and a real ₹45 delivery with both distances and an Accept
         * button was being held back from ACCEPT for it.
         */
        const val W_FARE = 0.45f
        const val W_TRIP_KM = 0.30f
        const val W_TRIP_MIN = 0.07f
        const val W_PICKUP_KM = 0.15f
        const val W_PICKUP_MIN = 0.03f

        const val POSITIONAL_PENALTY = 0.10f
        const val MISMATCH_PENALTY = 0.15f

        /** A fare found outside the offer card is trusted, but less. */
        const val RESCUE_PENALTY = 0.20f
        const val OCR_FACTOR = 0.9f

        const val LABELLED = 0.95f
        const val POSITIONAL = 0.60f
    }
}
