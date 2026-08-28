package com.ridescore.app.overlay

import com.ridescore.app.TestFixtures
import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.settings.OverlayMode
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.engine.RideScoreEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The card the driver actually reads. */
class OverlayPresenterTest {

    private val engine = RideScoreEngine()
    private val detailed = RideScoreSettings.DEFAULT.copy(overlayMode = OverlayMode.DETAILED)
    private val quick = RideScoreSettings.DEFAULT.copy(overlayMode = OverlayMode.QUICK)

    @Test
    fun `detailed card matches the brief's layout`() {
        val analysis = engine.analyse(TestFixtures.rapido(TestFixtures.RAPIDO_OFFER_A), detailed)
        val content = OverlayPresenter.present(analysis, detailed)!!

        assertEquals("🔴 REJECT", content.header)
        assertEquals("₹60", content.primary)
        assertEquals("7.7 km • 19 min", content.detailLines[0])
        assertEquals("₹112 net/hr", content.detailLines[1])
        assertEquals("₹4.59 net/km", content.detailLines[2])
    }

    @Test
    fun `quick card leads with the hourly rate`() {
        val good = listOf("Bike", "₹210", "Pickup 1.0 km", "Trip 9.0 km", "Trip time 22 mins")
        val analysis = engine.analyse(TestFixtures.rapido(good), quick)
        val content = OverlayPresenter.present(analysis, quick)!!

        assertEquals("🟢 ACCEPT", content.header)
        assertTrue(content.primary.endsWith("/hr"))
        assertEquals(1, content.detailLines.size)
        assertEquals("10 km • 26 min", content.detailLines[0])
    }

    @Test
    fun `quick card can drop the extra line entirely`() {
        val settings = quick.copy(overlayShowDetailsInQuickMode = false)
        val analysis = engine.analyse(TestFixtures.rapido(TestFixtures.RAPIDO_OFFER_A), settings)
        val content = OverlayPresenter.present(analysis, settings)!!
        assertTrue(content.detailLines.isEmpty())
    }

    @Test
    fun `two poor offers say no good order`() {
        val analysis = engine.analyse(
            TestFixtures.rapido(TestFixtures.RAPIDO_OFFER_A + TestFixtures.RAPIDO_OFFER_B),
            detailed,
        )
        val content = OverlayPresenter.present(analysis, detailed)!!

        assertEquals("🔴 NO GOOD ORDER", content.header)
        assertEquals(1, content.otherOffers.size)
        assertTrue(content.otherOffers[0].startsWith("🔴 ₹66"))
    }

    @Test
    fun `the best of several offers is labelled as such`() {
        val good = listOf("Bike", "₹210", "Pickup 1.0 km", "Trip 9.0 km", "Trip time 22 mins")
        val analysis = engine.analyse(
            TestFixtures.rapido(TestFixtures.RAPIDO_OFFER_A + good),
            detailed,
        )
        val content = OverlayPresenter.present(analysis, detailed)!!
        assertEquals("🟢 BEST OF 2", content.header)
    }

    @Test
    fun `at most three offers are shown`() {
        val extra = listOf("Bike", "₹80", "Pickup 2.0 km", "Trip 4.0 km", "Trip time 10 mins")
        val analysis = engine.analyse(
            TestFixtures.rapido(
                TestFixtures.RAPIDO_OFFER_A + TestFixtures.RAPIDO_OFFER_B + extra + extra,
            ),
            detailed,
        )
        val content = OverlayPresenter.present(analysis, detailed)!!
        assertTrue(analysis.ranked.size >= 4)
        assertEquals(2, content.otherOffers.size)
    }

    @Test
    fun `an unreadable fare says check, not a recommendation`() {
        val analysis = engine.analyse(
            TestFixtures.rapido(listOf("Bike", "Pickup 1.8 km", "Trip 5.9 km", "Trip time 12 mins")),
            detailed,
        )
        val content = OverlayPresenter.present(analysis, detailed)!!

        assertEquals("⚪ CHECK", content.header)
        assertEquals("Could not read fare", content.primary)
        assertEquals(Decision.CHECK, content.decision)
    }

    @Test
    fun `the destination is shown when it was read`() {
        val analysis = engine.analyse(
            TestFixtures.rapido(
                listOf(
                    "₹210", "Pickup 1.0 km", "Kondapur", "Drop 9.0 km", "Nallagandla",
                    "Trip time 22 mins",
                ),
            ),
            detailed,
        )
        val content = OverlayPresenter.present(analysis, detailed)!!
        assertTrue(content.detailLines.any { it == "→ Nallagandla" })
    }
}
