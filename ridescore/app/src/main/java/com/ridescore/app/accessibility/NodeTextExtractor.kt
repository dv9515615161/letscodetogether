package com.ridescore.app.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.ridescore.app.domain.model.ScreenSnapshot
import com.ridescore.app.domain.model.SourceApp
import com.ridescore.app.domain.model.TextBlock
import com.ridescore.app.domain.model.TextSource
import com.ridescore.app.parser.Extractors
import com.ridescore.app.parser.TextNormalizer

/**
 * Reads the visible text of the foreground app out of the accessibility tree.
 *
 * This is the fast path and the only path RideScore needs on a normal offer
 * screen: the text is already exact, so there is nothing to recognise and no
 * pixels to process.
 *
 * While walking the tree it also works out where one offer card ends and the
 * next begins. A node is treated as a card when its subtree contains both a
 * fare and a distance and no descendant of it does - the smallest self-contained
 * offer. That is what lets RideScore rank two Rapido offers correctly instead of
 * mashing them into one.
 *
 * Cost control: the walk is bounded by a node budget and a depth cap, skips
 * anything not visible to the user, and does no allocation beyond the strings it
 * keeps.
 */
object NodeTextExtractor {

    const val MAX_NODES = 500
    const val MAX_DEPTH = 30
    const val MAX_LINE_LENGTH = 120

    fun extract(
        root: AccessibilityNodeInfo?,
        packageName: String,
        capturedAtMillis: Long = System.currentTimeMillis(),
    ): ScreenSnapshot {
        if (root == null) {
            return ScreenSnapshot(
                packageName = packageName,
                sourceApp = SourceApp.fromPackage(packageName),
                blocks = emptyList(),
                allLines = emptyList(),
                capturedAtMillis = capturedAtMillis,
            )
        }

        val budget = Budget(MAX_NODES)
        val allLines = mutableListOf<String>()
        val cards = mutableListOf<TextBlock>()

        walk(root, 0, budget, allLines, cards)

        return ScreenSnapshot(
            packageName = packageName,
            sourceApp = SourceApp.fromPackage(packageName),
            blocks = cards.sortedBy { it.top },
            allLines = allLines,
            capturedAtMillis = capturedAtMillis,
            textSource = TextSource.ACCESSIBILITY,
        )
    }

    private class Budget(var remaining: Int) {
        fun take(): Boolean {
            if (remaining <= 0) return false
            remaining -= 1
            return true
        }
    }

    /** What a subtree contained, so a parent can decide whether it is a card. */
    private class Sub {
        val lines = mutableListOf<String>()
        var money = false
        var distance = false
        var cardClaimed = false
        var top = Int.MAX_VALUE
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int,
        budget: Budget,
        allLines: MutableList<String>,
        cards: MutableList<TextBlock>,
    ): Sub {
        val sub = Sub()
        if (depth > MAX_DEPTH || !budget.take()) return sub
        if (!node.isVisibleToUser) return sub

        val own = lineOf(node)
        if (own != null) {
            sub.lines += own
            allLines += own
            val normalized = TextNormalizer.normalize(own)
            if (Extractors.amounts(normalized).isNotEmpty()) sub.money = true
            if (Extractors.distancesKm(normalized).isNotEmpty()) sub.distance = true
        }

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        sub.top = bounds.top

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val childSub = walk(child, depth + 1, budget, allLines, cards)
            sub.lines += childSub.lines
            sub.money = sub.money || childSub.money
            sub.distance = sub.distance || childSub.distance
            sub.cardClaimed = sub.cardClaimed || childSub.cardClaimed
            if (childSub.top < sub.top) sub.top = childSub.top
        }

        // Smallest subtree that holds a whole offer wins the card.
        if (!sub.cardClaimed && sub.money && sub.distance && sub.lines.size >= 2) {
            cards += TextBlock(sub.lines.toList(), sub.top)
            sub.cardClaimed = true
        }

        return sub
    }

    /** Node text, or its content description when the text is empty. */
    private fun lineOf(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()?.trim()
        val line = if (!text.isNullOrEmpty()) text else node.contentDescription?.toString()?.trim()
        if (line.isNullOrEmpty()) return null
        if (line.length > MAX_LINE_LENGTH) return line.substring(0, MAX_LINE_LENGTH)
        return line
    }
}
