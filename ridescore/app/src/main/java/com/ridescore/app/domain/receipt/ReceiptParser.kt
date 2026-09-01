package com.ridescore.app.domain.receipt

import com.ridescore.app.domain.model.ScreenSnapshot
import com.ridescore.app.domain.model.SourceApp
import com.ridescore.app.domain.model.TextSource
import com.ridescore.app.parser.TextNormalizer

/**
 * Reads a finished order's details screen.
 *
 * The screen this parses looks like:
 * ```
 * Bike Order Details
 * 8:39 am | 29 August, 2026
 * Your Earning
 * ₹28.54
 * 1.17 km · 4.72 min
 * Payment info
 * Customer Fare            ₹40
 * Government Taxes and Other Fees   -₹5.46
 * Commission (16.00% of Customer Fare)  -₹6
 * Total Earning            ₹28.54
 * ```
 *
 * Two things make it worth parsing rather than merely ignoring. It is the only
 * screen that says what a ride *really* took - "1.17 km · 4.72 min" is
 * measured, not estimated - and it is the only one that says what really
 * arrived. Everything else in RideScore is a forecast; this is the outcome.
 *
 * Conservative by design. A receipt must show an earning **and** at least one
 * of the receipt-only labels, or nothing is returned: mistaking a live offer
 * for a finished ride would write a ride that never happened into the log.
 */
object ReceiptParser {

    /** Words that appear only after a ride is over. */
    private val RECEIPT_MARKERS = listOf(
        "order details", "payment info", "total earning", "your earning",
        "customer fare", "government taxes",
    )

    /**
     * The payment table's rows, in the order Rapido prints them.
     *
     * Used to read the table as a table. Rapido lays it out in two columns,
     * and depending on how the view tree is walked the text arrives either
     * interleaved - label, amount, label, amount - or as every label followed
     * by every amount. Both happen, so both are handled: the labels are found
     * in order, the amounts that follow the table are found in order, and they
     * are matched up.
     */
    private val TABLE_ROWS = listOf(
        "customer fare", "customer extra", "government taxes", "commission",
        "total earning",
    )

    /** If any of these is on screen it is a live offer, not a receipt. */
    private val LIVE_MARKERS = listOf("accept", "confirm", "decline", "reject")

    private val MONEY = Regex("""₹\s*(\d+(?:\.\d{1,2})?)""")
    private val KM = Regex("""(\d+(?:\.\d{1,2})?)\s*km""")
    private val MIN = Regex("""(\d+(?:\.\d{1,2})?)\s*min""")
    private val TIME_OF_DAY = Regex("""\d{1,2}:\d{2}\s*(am|pm)""")

    fun parse(snapshot: ScreenSnapshot): RideReceipt? {
        if (snapshot.isEmpty) return null
        val ocr = snapshot.textSource == TextSource.OCR
        val lines = snapshot.allLines.map { TextNormalizer.normalize(it, ocr) }

        var markers = 0
        for (line in lines) {
            if (LIVE_MARKERS.any { line.length <= 24 && line.contains(it) }) return null
            if (RECEIPT_MARKERS.any { line.contains(it) }) markers++
        }
        if (markers < MIN_MARKERS) return null

        val raw = snapshot.allLines
        val table = readTable(lines, raw)

        // "Total Earning" is the last row of the table on a commission-plan
        // receipt. A subscription-plan one has no such row at all - the amount
        // is at the top under "Your Earning" - so both are tried.
        val total = table["total earning"]
            ?: labelledMoney(lines, raw, "total earning")
            ?: labelledMoney(lines, raw, "your earning")
            ?: return null

        return RideReceipt(
            sourceApp = snapshot.sourceApp,
            totalEarning = total,
            customerFare = table["customer fare"] ?: labelledMoney(lines, raw, "customer fare"),
            customerExtra = table["customer extra"],
            commission = table["commission"] ?: labelledMoney(lines, raw, "commission"),
            taxesAndFees = table["government taxes"]
                ?: labelledMoney(lines, raw, "government taxes")
                ?: labelledMoney(lines, raw, "taxes and other fees"),
            tripKm = firstMatch(raw, KM),
            tripMinutes = firstMatch(raw, MIN),
            pickupKm = labelledNumber(lines, raw, "pickup", KM),
            rideType = rideType(raw),
            orderTime = raw.firstNotNullOfOrNull { TIME_OF_DAY.find(it.lowercase())?.value },
            signature = signature(total, raw),
        )
    }

    /** True when this is a receipt, without doing the work of reading it. */
    fun looksLikeReceipt(snapshot: ScreenSnapshot): Boolean = parse(snapshot) != null

    /**
     * Read the payment table, whichever way its two columns arrived.
     *
     * Everything from "Payment info" down is taken as the table. Each labelled
     * row is noted in the order it appears, along with any amount on that same
     * line; the amounts left over - those on lines of their own - are then
     * handed to the rows that did not get one, in order. That covers the
     * interleaved layout and the two-column one with the same pass, and reads
     * nothing above "Payment info", so the "₹8.9 saved with subscription" and
     * "₹10 Extra from Customer" lines higher up cannot be mistaken for rows.
     */
    private fun readTable(lines: List<String>, raw: List<String>): Map<String, Double> {
        val start = lines.indexOfFirst { it.contains("payment info") }
        if (start < 0) return emptyMap()

        val rowsInOrder = mutableListOf<String>()
        val found = mutableMapOf<String, Double>()
        val looseAmounts = mutableListOf<Double>()

        for (i in start + 1..lines.lastIndex) {
            val row = TABLE_ROWS.firstOrNull { lines[i].contains(it) }
            val amount = MONEY.find(raw[i])?.groupValues?.get(1)?.toDoubleOrNull()
            when {
                row != null && amount != null -> found[row] = amount
                row != null -> rowsInOrder += row
                amount != null -> looseAmounts += amount
            }
        }

        // Whatever is left over lines up in order with the rows still waiting.
        rowsInOrder.forEachIndexed { index, row ->
            looseAmounts.getOrNull(index)?.let { found.putIfAbsent(row, it) }
        }
        return found
    }

    /**
     * The amount on, or just after, a labelled line.
     *
     * Both layouts occur: "Customer Fare ₹40" on one line, and the label and
     * the amount as separate lines in a two-column table.
     */
    private fun labelledMoney(lines: List<String>, raw: List<String>, label: String): Double? {
        for (i in lines.indices) {
            if (!lines[i].contains(label)) continue
            MONEY.find(raw[i])?.let { return it.groupValues[1].toDoubleOrNull() }
            for (j in i + 1..minOf(i + 2, raw.lastIndex)) {
                // Stop at the next label so a missing amount does not steal
                // the row below it.
                if (RECEIPT_MARKERS.any { lines[j].contains(it) } && j != i) break
                MONEY.find(raw[j])?.let { return it.groupValues[1].toDoubleOrNull() }
            }
        }
        return null
    }

    private fun labelledNumber(
        lines: List<String>,
        raw: List<String>,
        label: String,
        pattern: Regex,
    ): Double? {
        for (i in lines.indices) {
            if (lines[i].contains(label)) {
                pattern.find(raw[i])?.let { return it.groupValues[1].toDoubleOrNull() }
            }
        }
        return null
    }

    private fun firstMatch(raw: List<String>, pattern: Regex): Double? =
        raw.firstNotNullOfOrNull { pattern.find(it)?.groupValues?.get(1)?.toDoubleOrNull() }

    private fun rideType(raw: List<String>): String? =
        raw.firstOrNull { it.contains("Order Details", ignoreCase = true) }
            ?.substringBefore("Order Details")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** Enough to recognise the same receipt if the screen repaints. */
    private fun signature(total: Double, raw: List<String>): String =
        "%.2f/%s".format(total, raw.take(4).joinToString("|").take(80))

    /** One marker could be a coincidence; two is a receipt. */
    private const val MIN_MARKERS = 2
}
