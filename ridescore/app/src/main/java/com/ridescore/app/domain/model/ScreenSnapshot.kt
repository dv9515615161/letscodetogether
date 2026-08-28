package com.ridescore.app.domain.model

/**
 * Text read from one screen, already grouped into candidate offer cards.
 *
 * Deliberately free of Android types so the whole parse -> calculate -> decide
 * path is unit-testable on the JVM.
 */
data class ScreenSnapshot(
    val packageName: String,
    val sourceApp: SourceApp,
    /** One entry per candidate offer card, in top-to-bottom screen order. */
    val blocks: List<TextBlock>,
    /** Every line on screen, in order. Used by fallback parsing strategies. */
    val allLines: List<String>,
    val capturedAtMillis: Long = 0L,
    val textSource: TextSource = TextSource.ACCESSIBILITY,
) {
    /** Cheap identity of the screen content, used to skip duplicate work. */
    val signature: String by lazy {
        val sb = StringBuilder(packageName).append('|').append(textSource.name)
        allLines.forEach { sb.append('|').append(it) }
        sb.toString().hashCode().toString(16)
    }

    val isEmpty: Boolean get() = allLines.isEmpty()

    companion object {
        fun of(
            packageName: String,
            lines: List<String>,
            blocks: List<List<String>>? = null,
            textSource: TextSource = TextSource.ACCESSIBILITY,
            capturedAtMillis: Long = 0L,
        ): ScreenSnapshot = ScreenSnapshot(
            packageName = packageName,
            sourceApp = SourceApp.fromPackage(packageName),
            blocks = (blocks ?: listOf(lines)).map { TextBlock(it) },
            allLines = lines,
            capturedAtMillis = capturedAtMillis,
            textSource = textSource,
        )
    }
}

data class TextBlock(
    val lines: List<String>,
    /** Vertical position on screen when known. Used only for ordering. */
    val top: Int = 0,
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}
