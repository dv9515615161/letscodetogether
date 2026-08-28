package com.ridescore.app.engine

import com.ridescore.app.domain.model.ScreenAnalysis
import com.ridescore.app.domain.model.ScreenSnapshot
import com.ridescore.app.domain.settings.RideScoreSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the overlay and the voice announcer consume. */
data class PipelineResult(
    val analysis: ScreenAnalysis,
    val sequence: Long,
    val fromCache: Boolean,
)

/** Counters for the diagnostics screen and for the performance tests. */
data class PipelineStats(
    val submitted: Long = 0,
    val analysed: Long = 0,
    val skippedDuplicate: Long = 0,
    val servedFromCache: Long = 0,
    val ocrPasses: Long = 0,
)

/**
 * Keeps the newest screen winning.
 *
 * Offer screens can change many times a second - a countdown ticking, a second
 * offer sliding in - and each change fires accessibility events. Three
 * mechanisms keep that cheap:
 *
 *  1. **Conflation.** The inbox is a [Channel.CONFLATED] channel, so a snapshot
 *     that arrives while another is being analysed simply replaces anything
 *     waiting. Work never queues up, and a stale offer is dropped rather than
 *     analysed late.
 *  2. **Duplicate suppression.** Identical screen text is recognised by
 *     signature and skipped outright.
 *  3. **Result cache.** A small LRU keyed by screen signature and settings
 *     serves repeat screens without re-parsing.
 */
class OfferPipeline(
    private val engine: RideScoreEngine,
    private val settingsProvider: () -> RideScoreSettings,
    private val scope: CoroutineScope,
    private val ocrProvider: OcrFallbackProvider? = null,
    private val cacheSize: Int = 16,
) {

    private val inbox = Channel<ScreenSnapshot>(Channel.CONFLATED)

    private val _results = MutableStateFlow<PipelineResult?>(null)
    val results: StateFlow<PipelineResult?> = _results.asStateFlow()

    private val _stats = MutableStateFlow(PipelineStats())
    val stats: StateFlow<PipelineStats> = _stats.asStateFlow()

    private val cache = object : LinkedHashMap<String, ScreenAnalysis>(cacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ScreenAnalysis>?) =
            size > cacheSize
    }

    private var worker: Job? = null
    private var lastSignature: String? = null
    private var sequence = 0L

    fun start() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            for (snapshot in inbox) {
                process(snapshot)
            }
        }
    }

    fun stop() {
        worker?.cancel()
        worker = null
    }

    /** Non-blocking. Safe to call from an accessibility event callback. */
    fun submit(snapshot: ScreenSnapshot) {
        _stats.value = _stats.value.copy(submitted = _stats.value.submitted + 1)
        inbox.trySend(snapshot)
    }

    /** Called when the driver leaves a supported app, so the overlay can hide. */
    fun clear() {
        lastSignature = null
        _results.value = null
    }

    /** Settings changed - previously cached decisions no longer apply. */
    fun invalidateCache() {
        cache.clear()
        lastSignature = null
    }

    private suspend fun process(snapshot: ScreenSnapshot) {
        val settings = settingsProvider()

        if (snapshot.signature == lastSignature) {
            _stats.value = _stats.value.copy(skippedDuplicate = _stats.value.skippedDuplicate + 1)
            return
        }

        val key = cacheKey(snapshot, settings)
        cache[key]?.let { cached ->
            lastSignature = snapshot.signature
            _stats.value = _stats.value.copy(servedFromCache = _stats.value.servedFromCache + 1)
            _results.value = PipelineResult(cached, ++sequence, fromCache = true)
            return
        }

        var analysis = engine.analyse(snapshot, settings)
        _stats.value = _stats.value.copy(analysed = _stats.value.analysed + 1)

        // OCR is a fallback, never the first move: it only runs when the
        // accessibility text did not yield a usable offer.
        val ocr = ocrProvider
        if (ocr != null && ocr.isAvailable() && engine.needsOcrFallback(analysis, settings)) {
            val ocrSnapshot = ocr.capture(snapshot.packageName)
            if (ocrSnapshot != null && !ocrSnapshot.isEmpty) {
                val ocrAnalysis = engine.analyse(ocrSnapshot, settings)
                _stats.value = _stats.value.copy(
                    analysed = _stats.value.analysed + 1,
                    ocrPasses = _stats.value.ocrPasses + 1,
                )
                if (betterOf(ocrAnalysis, analysis)) analysis = ocrAnalysis
            }
        }

        cache[key] = analysis
        lastSignature = snapshot.signature
        _results.value = PipelineResult(analysis, ++sequence, fromCache = false)
    }

    private fun betterOf(candidate: ScreenAnalysis, current: ScreenAnalysis): Boolean {
        val c = candidate.best?.confidence ?: 0f
        val n = current.best?.confidence ?: 0f
        return c > n
    }

    private fun cacheKey(snapshot: ScreenSnapshot, settings: RideScoreSettings): String =
        snapshot.signature + "@" + settings.hashCode().toString(16)
}
