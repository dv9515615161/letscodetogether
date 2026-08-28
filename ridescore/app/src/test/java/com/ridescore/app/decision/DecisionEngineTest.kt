package com.ridescore.app.decision

import com.ridescore.app.TestFixtures.offer
import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.model.DecisionReason
import com.ridescore.app.domain.settings.RideScoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionEngineTest {

    private val engine = DecisionEngine()
    private val settings = RideScoreSettings.DEFAULT

    private fun decide(
        netPerHour: Double,
        netPerKm: Double = 12.0,
        confidence: Float = 0.95f,
        s: RideScoreSettings = settings,
    ) = engine.decide(
        DecisionInput(
            offer = offer(confidence = confidence),
            totalDistanceKm = 7.7,
            totalTimeMinutes = 19.0,
            netPerHour = netPerHour,
            netPerKm = netPerKm,
            confidence = confidence,
        ),
        s,
    )

    @Test
    fun `at or above 150 net per hour is accept`() {
        assertEquals(Decision.ACCEPT, decide(150.0).decision)
        assertEquals(Decision.ACCEPT, decide(220.0).decision)
    }

    @Test
    fun `120 to 149 net per hour is maybe`() {
        assertEquals(Decision.MAYBE, decide(120.0).decision)
        assertEquals(Decision.MAYBE, decide(149.99).decision)
    }

    @Test
    fun `below 120 net per hour is reject`() {
        assertEquals(Decision.REJECT, decide(119.99).decision)
        assertEquals(Decision.REJECT, decide(111.66).decision)
    }

    @Test
    fun `a good hourly rate with a poor per km rate is only a maybe`() {
        val outcome = decide(netPerHour = 260.0, netPerKm = 4.3)
        assertEquals(Decision.MAYBE, outcome.decision)
        assertTrue(outcome.reasons.contains(DecisionReason.PER_KM_TOO_LOW))
    }

    @Test
    fun `the per km rule can be switched off`() {
        val outcome = decide(
            netPerHour = 260.0,
            netPerKm = 4.3,
            s = settings.copy(requireBothMetrics = false),
        )
        assertEquals(Decision.ACCEPT, outcome.decision)
    }

    @Test
    fun `thresholds are configurable`() {
        val strict = settings.copy(acceptNetPerHour = 200.0, maybeNetPerHour = 180.0)
        assertEquals(Decision.REJECT, decide(150.0, s = strict).decision)
        assertEquals(Decision.MAYBE, decide(185.0, s = strict).decision)
        assertEquals(Decision.ACCEPT, decide(205.0, s = strict).decision)
    }

    @Test
    fun `a shaky read is never an accept`() {
        val outcome = decide(netPerHour = 300.0, confidence = 0.7f)
        assertEquals(Decision.MAYBE, outcome.decision)
        assertTrue(outcome.reasons.contains(DecisionReason.LOW_CONFIDENCE_CAPPED))
    }

    @Test
    fun `a read below the usable floor is a check, not a recommendation`() {
        assertEquals(Decision.CHECK, decide(netPerHour = 300.0, confidence = 0.3f).decision)
    }

    @Test
    fun `missing fare, distance or time all end up as check`() {
        val noFare = engine.decide(
            DecisionInput(offer(totalFare = null), 7.7, 19.0, 0.0, 0.0, 0.9f),
            settings,
        )
        assertEquals(Decision.CHECK, noFare.decision)
        assertTrue(noFare.reasons.contains(DecisionReason.MISSING_FARE))

        val noDistance = engine.decide(
            DecisionInput(offer(tripKm = null, pickupKm = null), 0.0, 19.0, 0.0, 0.0, 0.9f),
            settings,
        )
        assertEquals(Decision.CHECK, noDistance.decision)
        assertTrue(noDistance.reasons.contains(DecisionReason.MISSING_DISTANCE))

        val noTime = engine.decide(
            DecisionInput(offer(tripMin = null), 7.7, 0.0, 0.0, 0.0, 0.9f),
            settings,
        )
        assertEquals(Decision.CHECK, noTime.decision)
        assertTrue(noTime.reasons.contains(DecisionReason.MISSING_TIME))
    }
}
