package com.ridescore.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ridescore.app.data.settings.SettingsCache
import com.ridescore.app.data.settings.SettingsRepository
import com.ridescore.app.domain.model.ScreenAnalysis
import com.ridescore.app.domain.model.SourceApp
import com.ridescore.app.engine.OfferPipeline
import com.ridescore.app.engine.RideScoreEngine
import com.ridescore.app.ocr.MlKitOcrProvider
import com.ridescore.app.overlay.OverlayController
import com.ridescore.app.overlay.OverlayVisibility
import com.ridescore.app.tts.VoiceAnnouncer
import com.ridescore.app.util.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The one component that reads the screen.
 *
 * What it does: when Rapido or Uber is in the foreground it reads the text of
 * the current screen through the public accessibility APIs, hands it to the
 * local decision engine, and shows an advisory card.
 *
 * What it deliberately does not do: it never calls `performAction` on anything,
 * never dispatches a gesture, never taps, swipes or scrolls, and never accepts
 * or declines an offer. RideScore is read-only by construction - there is no
 * code path in this file that can touch another app's UI. The driver decides.
 *
 * It also never looks inside an app it does not support: the package name on
 * the event is checked before the window content is requested, so no other
 * app's screen is ever read.
 */
class RideScoreAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var pipeline: OfferPipeline
    private var overlay: OverlayController? = null
    private var voice: VoiceAnnouncer? = null

    private val keyguardManager: KeyguardManager? by lazy {
        getSystemService(KeyguardManager::class.java)
    }

    /**
     * Android does not let any app draw a floating window above the lock
     * screen. Rapido can show its offer there because it is an activity, which
     * is allowed to; an overlay is not. So on the lock screen the reading and
     * the voice still work, and only the card is unavailable.
     */
    private val isLocked: Boolean get() = keyguardManager?.isKeyguardLocked == true

    private var lastScanUptime = 0L
    private var currentPackage: String? = null
    private var settingsVersion = -1L

    private val scanRunnable = Runnable {
        runCatching { scanNow() }.onFailure { Log.w(TAG, "Screen read failed", it) }
    }
    private val hideRunnable = Runnable { overlay?.hide() }

    /** All hiding goes through here, so a fresh reading always cancels a pending hide. */
    private fun scheduleHide(delayMillis: Long) {
        mainHandler.removeCallbacks(hideRunnable)
        mainHandler.postDelayed(hideRunnable, delayMillis)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        settingsRepository = SettingsRepository(applicationContext)
        val engine = RideScoreEngine()
        pipeline = OfferPipeline(
            engine = engine,
            settingsProvider = { SettingsCache.current },
            scope = scope,
            ocrProvider = MlKitOcrProvider(),
        )
        pipeline.start()

        overlay = OverlayController(this)
        voice = VoiceAnnouncer(applicationContext)

        scope.launch {
            settingsRepository.settings.collect { settings ->
                if (settingsVersion != SettingsCache.version) {
                    settingsVersion = SettingsCache.version
                    pipeline.invalidateCache()
                }
                if (!settings.overlayEnabled) mainHandler.post { overlay?.hide() }
            }
        }

        scope.launch {
            pipeline.results.filterNotNull().collect { result ->
                // Same reasoning as the pipeline worker: a failure while
                // presenting one result must not end the collector.
                runCatching { onAnalysis(result.analysis) }
                    .onFailure { Log.w(TAG, "Could not present the analysis", it) }
            }
        }

        Diagnostics.update { it.copy(serviceConnected = true) }
        Log.i(TAG, "RideScore accessibility service connected (read-only)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val received = event ?: return
        val packageName = received.packageName?.toString() ?: return

        // The status bar, the notification shade and the keyboard sit on top of
        // the driver app rather than replacing it. They fire window events
        // constantly and mean nothing here.
        if (OverlayVisibility.isTransientSystemPackage(packageName)) return

        val app = SourceApp.fromPackage(packageName)
        val settings = SettingsCache.current

        val supported = settings.watches(app)
        if (packageName != currentPackage) {
            currentPackage = packageName
            Diagnostics.update {
                it.copy(lastForegroundPackage = packageName, lastForegroundSupported = supported)
            }
        }

        if (!supported) {
            // Not a supported driver app: the window content is never requested.
            if (received.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                // Give it a moment - the driver may be bouncing off a dialog
                // and straight back into the offer.
                mainHandler.post { scheduleHide(OverlayVisibility.APP_SWITCH_GRACE_MS) }
                pipeline.clear()
            }
            return
        }

        when (received.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> scheduleScan()
        }
    }

    /**
     * Coalesces the burst of events an offer screen produces.
     *
     * The first change after a quiet moment is read immediately, so a new offer
     * shows up without any added delay. Changes that keep arriving - a ticking
     * countdown, an animating card - are collapsed into one read.
     */
    private fun scheduleScan() {
        val now = SystemClock.uptimeMillis()
        mainHandler.removeCallbacks(scanRunnable)
        if (now - lastScanUptime > IMMEDIATE_WINDOW_MS) {
            scanNow()
        } else {
            mainHandler.postDelayed(scanRunnable, DEBOUNCE_MS)
        }
    }

    private fun scanNow() {
        lastScanUptime = SystemClock.uptimeMillis()
        val packageName = currentPackage ?: return
        if (!SettingsCache.current.watches(SourceApp.fromPackage(packageName))) return

        // Every window the supported app owns, not just the active one: Rapido
        // floats its offer above the map in a window of its own, so reading the
        // active window alone can catch the map and miss the fare.
        val roots = rootsFor(packageName)
        if (roots.isEmpty()) return

        val snapshot = NodeTextExtractor.extract(roots, packageName)
        if (snapshot.isEmpty) return

        // Reference assignment only - nothing is copied, allocated or stored.
        Diagnostics.update {
            it.copy(
                lastLines = snapshot.allLines,
                lastBlocks = snapshot.blocks.map { block -> block.lines },
            )
        }
        pipeline.submit(snapshot)
    }

    /**
     * Window roots belonging to [packageName], and nothing else.
     *
     * The package is checked on every window before its content is touched, so
     * no other app's screen is ever read.
     */
    private fun rootsFor(packageName: String): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()

        rootInActiveWindow?.let { active ->
            if (active.packageName?.toString() == packageName) roots += active
        }

        runCatching {
            for (window in windows) {
                val root = window.root ?: continue
                if (root.packageName?.toString() != packageName) continue
                if (roots.none { it == root }) roots += root
            }
        }.onFailure { Log.w(TAG, "Could not list windows", it) }

        return roots
    }

    private fun onAnalysis(analysis: ScreenAnalysis) {
        val settings = SettingsCache.current
        Diagnostics.update {
            it.copy(
                lastOfferCount = analysis.ranked.size,
                lastAnalysisMicros = analysis.analysisDurationMillis * 1000,
                lastDecision = analysis.best?.decision?.label,
                lastConfidence = analysis.best?.confidence ?: 0f,
                lastUpdatedAtMillis = System.currentTimeMillis(),
                lastOfferSummaries = analysis.ranked.map(::describe),
            )
        }

        mainHandler.post {
            if (analysis.ranked.isEmpty()) {
                // One unreadable frame is not evidence the offer is gone. Offer
                // screens repaint several times a second and a repaint can be
                // caught half-drawn, so let the card stand for a few seconds.
                if (overlay?.isVisible == true) scheduleHide(OverlayVisibility.EMPTY_GRACE_MS)
                return@post
            }
            if (settings.overlayEnabled) {
                mainHandler.removeCallbacks(hideRunnable)
                val locked = isLocked
                // Still attempted when locked: a few ROMs allow it, and it
                // costs nothing when they do not.
                val shown = overlay?.show(analysis, settings) ?: false
                Diagnostics.update {
                    it.copy(
                        overlayPermissionMissing = !shown && !locked,
                        overlayBlockedByLockScreen = locked,
                    )
                }
                if (settings.overlayAutoHideMillis > 0) {
                    scheduleHide(settings.overlayAutoHideMillis)
                }
            }
        }

        if (settings.voiceEnabled) voice?.announce(analysis, settings)
    }

    /** One readable line per parsed offer, for the diagnostics screen. */
    private fun describe(analysis: com.ridescore.app.domain.model.RideAnalysis): String {
        val o = analysis.offer
        fun money(v: Double?) = v?.let { "₹" + it.toString().removeSuffix(".0") } ?: "?"
        fun km(v: Double?) = v?.let { "$it km" } ?: "?"
        fun min(v: Double?) = v?.let { "${it.toString().removeSuffix(".0")} min" } ?: "?"
        return buildString {
            append(analysis.decision.emoji).append(' ').append(analysis.decision.label)
            append("  fare=").append(money(o.totalFare))
            if (o.bonusFare != null) {
                append(" (").append(money(o.baseFare)).append(" + ").append(money(o.bonusFare)).append(')')
            }
            append("  pickup=").append(km(o.pickupDistanceKm))
            append("  trip=").append(km(o.tripDistanceKm))
            append("  time=").append(min(o.tripTimeMinutes))
            append("  confidence=").append((analysis.confidence * 100).toInt()).append('%')
        }
    }

    /** Used by the app's own home screen to preview the card. Never touches another app. */
    fun preview(analysis: ScreenAnalysis) {
        mainHandler.post {
            mainHandler.removeCallbacks(hideRunnable)
            overlay?.show(analysis, SettingsCache.current)
            mainHandler.postDelayed(hideRunnable, PREVIEW_MS)
        }
    }

    override fun onInterrupt() {
        mainHandler.post { overlay?.hide() }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (instance === this) instance = null
        mainHandler.removeCallbacksAndMessages(null)
        pipeline.stop()
        overlay?.destroy()
        overlay = null
        voice?.shutdown()
        voice = null
        scope.cancel()
        Diagnostics.update { it.copy(serviceConnected = false) }
    }

    companion object {
        private const val TAG = "RideScore"

        /** Read immediately if the screen has been quiet for this long. */
        const val IMMEDIATE_WINDOW_MS = 250L

        /** Otherwise collapse the burst into one read. */
        const val DEBOUNCE_MS = 100L

        private const val PREVIEW_MS = 6_000L

        @Volatile
        var instance: RideScoreAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null
    }
}
