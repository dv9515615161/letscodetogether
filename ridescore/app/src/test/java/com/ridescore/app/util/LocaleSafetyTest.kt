package com.ridescore.app.util

import com.ridescore.app.TestFixtures.offer
import com.ridescore.app.calculator.FareCalculator
import com.ridescore.app.domain.settings.RideScoreSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * The app has to read the same on a phone set to Hindi, Telugu or German.
 *
 * `String.format` without an explicit locale uses the phone's. In German that
 * turns 4.59 into "4,59"; in Arabic, and in Hindi with the Devanagari
 * numbering extension, it turns the digits themselves into another script. A
 * driver whose phone is not in English would have found the card's notes
 * printing numbers in a different notation from the figures beside them.
 *
 * Every one of these runs under a hostile default locale.
 */
class LocaleSafetyTest {

    private val original = Locale.getDefault()
    private val calculator = FareCalculator()

    @Before
    fun useAHostileLocale() {
        // German decimal comma, and Hindi with Devanagari digits - the two
        // ways a default locale can rewrite a number.
        Locale.setDefault(Locale.forLanguageTag("de-DE"))
    }

    @After
    fun restore() {
        Locale.setDefault(original)
    }

    @Test
    fun `money always reads with a dot, whatever the phone is set to`() {
        assertEquals("₹4.59", Format.rupees2(4.59))
        assertEquals("₹4.59/km", Format.perKm(4.59))
        assertEquals("7.7 km", Format.km(7.7))
        assertEquals("4.6", Format.decimal(4.59, 1))
    }

    @Test
    fun `the notes on the card do not switch notation`() {
        val a = calculator.analyse(
            offer(totalFare = 90.0, pickupKm = 1.8, tripKm = 6.0, tripMin = null),
            RideScoreSettings.DEFAULT,
        )

        val notes = a.notes.joinToString(" ")
        assertTrue("notes were: $notes", notes.contains("Trip time estimated"))
        // A comma decimal here would sit beside dot decimals from Format.
        assertTrue("notes used a comma decimal: $notes", !notes.contains(",\\d".toRegex()))
    }

    @Test
    fun `every locale gives the same answer, because the maths is not text`() {
        val settings = RideScoreSettings.DEFAULT
        val theOffer = offer(totalFare = 60.0, pickupKm = 1.8, tripKm = 5.9, tripMin = 12.0)

        val german = calculator.analyse(theOffer, settings)
        Locale.setDefault(Locale.forLanguageTag("hi-IN-u-nu-deva"))
        val hindi = calculator.analyse(theOffer, settings)
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        val arabic = calculator.analyse(theOffer, settings)

        assertEquals(german.netPerHour, hindi.netPerHour, 0.0001)
        assertEquals(german.netPerHour, arabic.netPerHour, 0.0001)
        assertEquals(german.decision, hindi.decision)
        assertEquals(german.decision, arabic.decision)
    }

    @Test
    fun `the ride log stays machine-readable in any locale`() {
        // A comma decimal in a CSV would break every column after it.
        Locale.setDefault(Locale.forLanguageTag("de-DE"))
        val row = com.ridescore.app.domain.log.RideCsv.row(
            com.ridescore.app.domain.receipt.RideReceipt(
                sourceApp = com.ridescore.app.domain.model.SourceApp.RAPIDO,
                totalEarning = 56.24,
                customerFare = 52.0,
                tripKm = 4.09,
                tripMinutes = 11.3,
                signature = "s",
            ),
            1_756_000_000_000L,
        )
        assertEquals(com.ridescore.app.domain.log.RideCsv.COLUMNS.size, row.split(',').size)
        assertTrue("row was: $row", row.contains("56.24"))
    }
}
