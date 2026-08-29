package com.ridescore.app.calculator

import com.ridescore.app.TestFixtures.offer
import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.settings.RideScoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A trip-count quest - "complete 15 more trips to make ₹250 extra" - pays the
 * same for every trip regardless of its size. The four Uber offers below are
 * real ones from one shift in Kukatpally.
 *
 * The point this pins down: a count quest does not lift every offer equally.
 * The bonus per trip is fixed, so its effect on the *hourly* rate is largest on
 * the shortest trip - which inverts the instinct to take the big fare.
 */
class CountQuestTest {

    private val calculator = FareCalculator()

    private val driverRules = RideScoreSettings.DEFAULT.copy(
        acceptNetPerHour = 160.0,
        maybeNetPerHour = 130.0,
        minNetPerKm = 5.0,
        pickupSpeedKmph = 22.0,
    )

    /** ₹250 for 15 trips: ₹16.67 on every offer, whatever its size. */
    private val quest = driverRules.copy(
        incentiveEnabled = true,
        incentiveBonus = 250.0,
        incentiveTripsTarget = 15,
    )

    // The offers, as RideScore read them off the screen.
    private val small = offer(totalFare = 38.12, pickupKm = 0.6, tripKm = 3.1, tripMin = 10.0, pickupMin = 4.0)
    private val medium = offer(totalFare = 68.56, pickupKm = 0.7, tripKm = 8.2, tripMin = 23.0, pickupMin = 4.0)
    private val large = offer(totalFare = 142.49, pickupKm = 1.4, tripKm = 15.9, tripMin = 38.0, pickupMin = 7.0)

    @Test
    fun `all three are rejects on the fare alone`() {
        assertEquals(112.63, calculator.analyse(small, driverRules).netPerHour, 0.01)
        assertEquals(89.07, calculator.analyse(medium, driverRules).netPerHour, 0.01)
        assertEquals(116.17, calculator.analyse(large, driverRules).netPerHour, 0.01)

        for (o in listOf(small, medium, large)) {
            assertEquals(Decision.REJECT, calculator.analyse(o, driverRules).decision)
        }
    }

    @Test
    fun `the quest turns the short cheap trip green and leaves the big one red`() {
        val smallWithQuest = calculator.analyse(small, quest)
        val mediumWithQuest = calculator.analyse(medium, quest)
        val largeWithQuest = calculator.analyse(large, quest)

        assertEquals(16.67, smallWithQuest.incentiveEarning, 0.01)
        assertEquals(16.67, largeWithQuest.incentiveEarning, 0.01)

        // Same ₹16.67 on each, but spread over 14 minutes instead of 45.
        assertEquals(184.06, smallWithQuest.netPerHour, 0.01)
        assertEquals(126.10, mediumWithQuest.netPerHour, 0.01)
        assertEquals(138.40, largeWithQuest.netPerHour, 0.01)

        // The short cheap one goes green; the 45-minute ₹142 one only reaches
        // amber, and the middle one stays red.
        assertEquals(Decision.ACCEPT, smallWithQuest.decision)
        assertEquals(Decision.REJECT, mediumWithQuest.decision)
        assertEquals(Decision.MAYBE, largeWithQuest.decision)
    }

    @Test
    fun `the shortest trip gains the most, which is the whole point`() {
        val smallGain = calculator.analyse(small, quest).netPerHour -
            calculator.analyse(small, driverRules).netPerHour
        val largeGain = calculator.analyse(large, quest).netPerHour -
            calculator.analyse(large, driverRules).netPerHour

        assertEquals(71.43, smallGain, 0.01)
        assertEquals(22.22, largeGain, 0.01)
        assertTrue(smallGain > largeGain * 3)
    }
}
