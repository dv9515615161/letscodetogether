package com.ridescore.app.parser

import com.ridescore.app.domain.model.ScreenSnapshot
import com.ridescore.app.domain.model.SourceApp

/**
 * The one place that knows which parsers exist.
 *
 * To support Ola: write `OlaParser : BaseOfferParser(SourceApp.OLA)` and add it
 * to [DEFAULT_PARSERS]. No other file changes.
 */
class ParserRegistry(private val parsers: List<RideOfferParser> = DEFAULT_PARSERS) {

    fun parserFor(app: SourceApp): RideOfferParser? = parsers.firstOrNull { it.sourceApp == app }

    fun parserFor(snapshot: ScreenSnapshot): RideOfferParser? =
        parsers.firstOrNull { it.sourceApp == snapshot.sourceApp && it.canParse(snapshot) }

    fun supports(app: SourceApp): Boolean = parsers.any { it.sourceApp == app }

    companion object {
        val DEFAULT_PARSERS: List<RideOfferParser> = listOf(RapidoParser(), UberParser())
    }
}
