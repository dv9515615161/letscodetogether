package com.ridescore.app.tts

import com.ridescore.app.TestFixtures
import com.ridescore.app.domain.settings.RideScoreSettings
import com.ridescore.app.engine.RideScoreEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VoicePhrasesTest {

    private val engine = RideScoreEngine()
    private val settings = RideScoreSettings.DEFAULT

    private fun phraseFor(lines: List<String>) =
        VoicePhrases.forAnalysis(engine.analyse(TestFixtures.rapido(lines), settings))

    @Test
    fun `a good offer is announced as a good order`() {
        val phrase = phraseFor(listOf("Bike", "₹210", "Pickup 1.0 km", "Trip 9.0 km", "Trip time 22 mins"))
        assertEquals("Good order, 411 net per hour.", phrase)
    }

    @Test
    fun `a poor offer is announced as a reject`() {
        assertEquals("Reject, 112 net per hour.", phraseFor(TestFixtures.RAPIDO_OFFER_A))
    }

    @Test
    fun `two poor offers are announced once, not twice`() {
        assertEquals(
            "No good order.",
            phraseFor(TestFixtures.RAPIDO_OFFER_A + TestFixtures.RAPIDO_OFFER_B),
        )
    }

    @Test
    fun `the best of several offers names how many there were`() {
        val good = listOf("Bike", "₹210", "Pickup 1.0 km", "Trip 9.0 km", "Trip time 22 mins")
        assertEquals(
            "Best of 2, 411 net per hour.",
            phraseFor(TestFixtures.RAPIDO_OFFER_A + good),
        )
    }

    @Test
    fun `an unreadable offer asks the driver to look`() {
        assertEquals(
            "Could not read fare. Check the screen.",
            phraseFor(listOf("Bike", "Pickup 1.8 km", "Trip 5.9 km", "Trip time 12 mins")),
        )
    }

    @Test
    fun `the same situation has the same signature and is not repeated`() {
        val a = engine.analyse(TestFixtures.rapido(TestFixtures.RAPIDO_OFFER_A), settings)
        val b = engine.analyse(TestFixtures.rapido(TestFixtures.RAPIDO_OFFER_A), settings)
        val c = engine.analyse(TestFixtures.rapido(TestFixtures.RAPIDO_OFFER_B), settings)

        assertEquals(VoicePhrases.signatureOf(a), VoicePhrases.signatureOf(b))
        assertNotEquals(VoicePhrases.signatureOf(a), VoicePhrases.signatureOf(c))
    }
}
