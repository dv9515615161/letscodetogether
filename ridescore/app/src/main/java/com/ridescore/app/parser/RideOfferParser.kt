package com.ridescore.app.parser

import com.ridescore.app.domain.model.RideOffer
import com.ridescore.app.domain.model.ScreenSnapshot
import com.ridescore.app.domain.model.SourceApp

/**
 * Reads offers out of one app's offer screen.
 *
 * One implementation per driver app. Everything downstream (calculator,
 * decision engine, overlay) works on [RideOffer] and never knows which app the
 * text came from, so adding Ola means adding a parser and nothing else.
 */
interface RideOfferParser {

    val sourceApp: SourceApp

    /** Cheap check: is this screen worth a full parse? */
    fun canParse(snapshot: ScreenSnapshot): Boolean

    /** Every offer visible on the screen, in top-to-bottom order. */
    fun parse(snapshot: ScreenSnapshot): List<RideOffer>
}
