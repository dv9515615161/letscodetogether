package com.ridescore.app.calculator

import com.ridescore.app.TestFixtures.offer
import com.ridescore.app.domain.settings.RideScoreSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Rapido's rate card charges a percentage commission *and* a flat handling fee
 * per order. A percentage alone cannot express the second one, and the flat
 * part is what quietly ruins small orders.
 */
class PlatformFeeTest {

    private val calculator = FareCalculator()

    /** 15% commission plus ₹5 an order. */
    private val withFees = RideScoreSettings.DEFAULT.copy(
        platformFeeEnabled = true,
        platformFeePercent = 15.0,
        platformFeeFixed = 5.0,
    )

    @Test
    fun `percentage and flat fee are both taken`() {
        val a = calculator.analyse(offer(totalFare = 100.0), withFees)
        assertEquals(20.0, a.platformFee, 0.001) // 15 + 5
    }

    @Test
    fun `a flat fee costs a small order far more than a big one`() {
        val small = calculator.analyse(offer(totalFare = 38.0), withFees)
        val large = calculator.analyse(offer(totalFare = 142.0), withFees)

        // ₹5 on ₹38 is 13% of the order; on ₹142 it is 3.5%.
        assertEquals(10.70, small.platformFee, 0.01)
        assertEquals(26.30, large.platformFee, 0.01)
        assertEquals(28.2, small.platformFee / small.grossEarning * 100, 0.1)
        assertEquals(18.5, large.platformFee / large.grossEarning * 100, 0.1)
    }

    @Test
    fun `the fee never exceeds what the order paid`() {
        val tiny = calculator.analyse(
            offer(totalFare = 3.0),
            withFees.copy(platformFeeFixed = 20.0),
        )
        assertEquals(3.0, tiny.platformFee, 0.001)
    }

    @Test
    fun `both are off by default, so net means after fuel and nothing else`() {
        val a = calculator.analyse(offer(totalFare = 100.0), RideScoreSettings.DEFAULT)
        assertEquals(0.0, a.platformFee, 0.001)
        assertEquals(0.0, RideScoreSettings.DEFAULT.platformFeeFixed, 0.001)
    }
}
