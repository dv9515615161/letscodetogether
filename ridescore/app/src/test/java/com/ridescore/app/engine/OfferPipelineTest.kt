package com.ridescore.app.engine

import com.ridescore.app.TestFixtures
import com.ridescore.app.domain.model.ScreenAnalysis
import com.ridescore.app.domain.model.ScreenSnapshot
import com.ridescore.app.domain.model.SourceApp
import com.ridescore.app.domain.model.TextSource
import com.ridescore.app.domain.settings.RideScoreSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Performance behaviour: the newest offer on screen always wins, and work never
 * piles up behind it.
 */
class OfferPipelineTest {

    private val settings = RideScoreSettings.DEFAULT

    private fun offerLines(fare: Int) = listOf(
        "Bike",
        "₹$fare",
        "Pickup 1.8 km",
        "Trip 5.9 km",
        "Trip time 12 mins",
    )

    @Test
    fun `a burst of ten offers analyses only the newest`() = runTest {
        val engine = CountingEngine()
        val pipeline = OfferPipeline(engine, { settings }, this)
        pipeline.start()

        repeat(10) { i -> pipeline.submit(TestFixtures.rapido(offerLines(50 + i))) }
        advanceUntilIdle()

        assertEquals(1, engine.calls.get())
        assertEquals(1L, pipeline.stats.value.analysed)
        assertEquals(10L, pipeline.stats.value.submitted)
        assertEquals(59.0, pipeline.results.value!!.analysis.best!!.grossEarning, 0.001)

        pipeline.stop()
    }

    @Test
    fun `duplicate accessibility events are not analysed twice`() = runTest {
        val engine = CountingEngine()
        val pipeline = OfferPipeline(engine, { settings }, this)
        pipeline.start()

        val snapshot = TestFixtures.rapido(offerLines(60))
        pipeline.submit(snapshot)
        advanceUntilIdle()
        pipeline.submit(snapshot)
        advanceUntilIdle()
        pipeline.submit(TestFixtures.rapido(offerLines(60), capturedAt = 9_999L))
        advanceUntilIdle()

        assertEquals(1, engine.calls.get())
        assertEquals(2L, pipeline.stats.value.skippedDuplicate)

        pipeline.stop()
    }

    @Test
    fun `a repeat of an earlier screen is served from cache`() = runTest {
        val engine = CountingEngine()
        val pipeline = OfferPipeline(engine, { settings }, this)
        pipeline.start()

        pipeline.submit(TestFixtures.rapido(offerLines(60)))
        advanceUntilIdle()
        pipeline.submit(TestFixtures.rapido(offerLines(90)))
        advanceUntilIdle()
        pipeline.submit(TestFixtures.rapido(offerLines(60)))
        advanceUntilIdle()

        assertEquals(2, engine.calls.get())
        assertEquals(1L, pipeline.stats.value.servedFromCache)
        assertTrue(pipeline.results.value!!.fromCache)

        pipeline.stop()
    }

    @Test
    fun `changing settings invalidates cached decisions`() = runTest {
        val engine = CountingEngine()
        var current = settings
        val pipeline = OfferPipeline(engine, { current }, this)
        pipeline.start()

        pipeline.submit(TestFixtures.rapido(offerLines(60)))
        advanceUntilIdle()

        current = settings.copy(acceptNetPerHour = 90.0)
        pipeline.invalidateCache()
        pipeline.submit(TestFixtures.rapido(offerLines(60)))
        advanceUntilIdle()

        assertEquals(2, engine.calls.get())
        pipeline.stop()
    }

    @Test
    fun `leaving a supported app clears the current result`() = runTest {
        val pipeline = OfferPipeline(RideScoreEngine(), { settings }, this)
        pipeline.start()

        pipeline.submit(TestFixtures.rapido(offerLines(60)))
        advanceUntilIdle()
        assertNotNull(pipeline.results.value)

        pipeline.clear()
        assertNull(pipeline.results.value)

        pipeline.stop()
    }

    @Test
    fun `ocr runs only when accessibility text was not enough`() = runTest {
        val ocr = FakeOcr(
            TestFixtures.rapido(offerLines(60), source = TextSource.OCR),
        )
        val pipeline = OfferPipeline(RideScoreEngine(), { settings.copy(ocrFallbackEnabled = true) }, this, ocr)
        pipeline.start()

        // A screen the accessibility layer read cleanly: no OCR.
        pipeline.submit(TestFixtures.rapido(offerLines(60)))
        advanceUntilIdle()
        assertEquals(0, ocr.calls.get())

        // A screen with an unreadable fare: OCR fills the gap.
        pipeline.submit(
            TestFixtures.rapido(listOf("Bike", "Pickup 1.8 km", "Trip 5.9 km", "Trip time 12 mins")),
        )
        advanceUntilIdle()
        assertEquals(1, ocr.calls.get())
        assertEquals(60.0, pipeline.results.value!!.analysis.best!!.grossEarning, 0.001)

        pipeline.stop()
    }

    @Test
    fun `a slow device does not build a backlog and still ends on the newest offer`() {
        val engine = SlowEngine(delayMillis = 25)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val pipeline = OfferPipeline(engine, { settings }, scope)
        pipeline.start()

        runBlocking {
            repeat(10) { i ->
                pipeline.submit(TestFixtures.rapido(offerLines(50 + i)))
                delay(2)
            }
            withTimeout(5_000) {
                while (pipeline.results.value?.analysis?.best?.grossEarning != 59.0) delay(5)
            }
        }

        // Ten offers in ~20 ms at 25 ms per analysis: only the one in flight and
        // the newest survive. Everything in between is dropped, not queued. The
        // bound is loose because this test uses real threads and a loaded CI
        // machine stretches the timings; the exact-conflation guarantee is
        // pinned down by the deterministic burst test above.
        assertTrue(
            "analysed ${engine.calls.get()} of 10 submissions",
            engine.calls.get() < 10,
        )
        assertEquals(59.0, pipeline.results.value!!.analysis.best!!.grossEarning, 0.001)

        pipeline.stop()
        scope.cancel()
    }

    // ---------------------------------------------------------------- doubles

    private open class CountingEngine : RideScoreEngine() {
        val calls = AtomicInteger(0)
        override fun analyse(snapshot: ScreenSnapshot, settings: RideScoreSettings): ScreenAnalysis {
            calls.incrementAndGet()
            return super.analyse(snapshot, settings)
        }
    }

    private class SlowEngine(private val delayMillis: Long) : CountingEngine() {
        override fun analyse(snapshot: ScreenSnapshot, settings: RideScoreSettings): ScreenAnalysis {
            Thread.sleep(delayMillis)
            return super.analyse(snapshot, settings)
        }
    }

    private class FakeOcr(private val snapshot: ScreenSnapshot?) : OcrFallbackProvider {
        val calls = AtomicInteger(0)
        override fun isAvailable() = true
        override suspend fun capture(packageName: String): ScreenSnapshot? {
            calls.incrementAndGet()
            return snapshot
        }
    }
}
