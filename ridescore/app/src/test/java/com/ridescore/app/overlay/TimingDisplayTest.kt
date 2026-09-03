package com.ridescore.app.overlay

import com.ridescore.app.TestFixtures
import com.ridescore.app.domain.settings.OverlayMode
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.engine.RideScoreEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uber prints the minutes to the pickup as well as the trip minutes; Rapido
 * prints only the trip. The card should make clear which totals are exact and
 * which contain an estimate.
 */
class TimingDisplayTest {

    private val engine = RideScoreEngine()
    private val detailed = RideScoreSettings.DEFAULT.copy(overlayMode = OverlayMode.DETAILED)
    private val quick = RideScoreSettings.DEFAULT.copy(overlayMode = OverlayMode.QUICK)

    private val uberOffer = listOf(
        "Bike Saver",
        "₹38.12",
        "4 min (0.6 km)",
        "Allwyn Colony, Kukatpally, Hyderabad, 500037",
        "10 mins (3.1 km)",
        "KPHB Metro Station, Kukatpally, Hyderabad, 500072",
        "Confirm",
    )

    @Test
    fun `Uber's own pickup minutes are used, not an estimate`() {
        val best = engine.analyse(TestFixtures.uber(uberOffer), detailed).best!!

        assertFalse(best.pickupTimeEstimated)
        // 4 shown + 10 shown, with nothing invented.
        assertTrue(best.totalTimeMinutes == 14.0)
        assertTrue(best.pickupTimeMinutesCounted == 4.0)
    }

    @Test
    fun `the card breaks the two legs out`() {
        val content = OverlayPresenter.present(engine.analyse(TestFixtures.uber(uberOffer), detailed), detailed)!!
        assertTrue(content.detailLines.any { it == "4 min to pickup + 10 min trip" })
    }

    @Test
    fun `an exact total carries no tilde`() {
        val content = OverlayPresenter.present(engine.analyse(TestFixtures.uber(uberOffer), quick), quick)!!
        assertTrue(content.detailLines.any { it.contains("14 min") })
        assertFalse(content.detailLines.any { it.contains("~") })
    }

    @Test
    fun `a Rapido total is marked as containing an estimate`() {
        val analysis = engine.analyse(TestFixtures.rapido(TestFixtures.RAPIDO_OFFER_A), quick)
        val content = OverlayPresenter.present(analysis, quick)!!

        assertTrue(analysis.best!!.pickupTimeEstimated)
        // 12 min trip read, 7 min pickup worked out: the total is approximate.
        assertTrue(content.detailLines.any { it.contains("~19 min") })
    }

    @Test
    fun `the estimated leg is marked in the breakdown too`() {
        val analysis = engine.analyse(TestFixtures.rapido(TestFixtures.RAPIDO_OFFER_A), detailed)
        val content = OverlayPresenter.present(analysis, detailed)!!
        assertTrue(content.detailLines.any { it == "~7 min to pickup + 12 min trip" })
    }
}
