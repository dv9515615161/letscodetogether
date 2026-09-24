package com.ridescore.app.domain.model

/**
 * A ride-hailing driver app RideScore can read offers from.
 *
 * Adding a new app (Ola, for example) is a three-step change:
 *  1. add an entry here with its package names,
 *  2. add a [com.ridescore.app.parser.RideOfferParser] implementation,
 *  3. register that parser in [com.ridescore.app.parser.ParserRegistry].
 * Nothing else in the pipeline is app-specific.
 */
enum class SourceApp(val displayName: String, val packageNames: Set<String>) {
    RAPIDO(
        displayName = "Rapido Captain",
        packageNames = setOf("com.rapido.rider"),
    ),
    UBER(
        displayName = "Uber Driver",
        packageNames = setOf("com.ubercab.driver"),
    ),
    OLA(
        displayName = "Ola Partner",
        packageNames = setOf("com.olacabs.oladriver"),
    ),
    UNKNOWN(
        displayName = "Unsupported app",
        packageNames = emptySet(),
    );

    companion object {
        /** Apps that have a parser today. OLA is declared but not yet parseable. */
        val SUPPORTED: List<SourceApp> = listOf(RAPIDO, UBER)

        private val byPackage: Map<String, SourceApp> =
            entries.flatMap { app -> app.packageNames.map { it to app } }.toMap()

        fun fromPackage(packageName: String?): SourceApp {
            if (packageName.isNullOrBlank()) return UNKNOWN
            return byPackage[packageName] ?: UNKNOWN
        }
    }
}
