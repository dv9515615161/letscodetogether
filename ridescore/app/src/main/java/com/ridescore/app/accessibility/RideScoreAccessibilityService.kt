package com.ridescore.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.ridescore.app.data.settings.SettingsCache
import com.ridescore.app.data.settings.SettingsRepository
import com.ridescore.app.domain.model.ScreenAnalysis
import com.ridescore.app.domain.model.SourceApp
import com.ridescore.app.engine.OfferPipeline
import com.ridescore.app.engine.RideScoreEngine
import com.ridescore.app.ocr.MlKitOcrProvider
import com.ridescore.app.overlay.OverlayController
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

    private var lastScanUptime = 0L
    private var currentPackage: String? = null
    private var settingsVersion = -1L

    private val scanRunnable = Runnable { scanNow() }
    private val hideRunnable = Runnable { overlay?.hide() }

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
                onAnalysis(result.analysis)
            }
        }

        Diagnostics.update { it.copy(serviceConnected = true) }
        Log.i(TAG, "RideScore accessibility service connected (read-only)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val received = event ?: return
        val packageName = received.packageName?.toString() ?: return
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
                mainHandler.post { overlay?.hide() }
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

        val root = rootInActiveWindow ?: return

        // The active window can belong to a different app than the event did -
        // a system dialog, a notification shade. Read it only if it is still the
        // supported app.
        val rootPackage = root.packageName?.toString() ?: return
        if (rootPackage != packageName) return

        val snapshot = NodeTextExtractor.extract(root, rootPackage)
        if (snapshot.isEmpty) return
        pipeline.submit(snapshot)
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
            )
        }

        mainHandler.post {
            mainHandler.removeCallbacks(hideRunnable)
            if (analysis.ranked.isEmpty()) {
                overlay?.hide()
                return@post
            }
            if (settings.overlayEnabled) {
                val shown = overlay?.show(analysis, settings) ?: false
                Diagnostics.update { it.copy(overlayPermissionMissing = !shown) }
                if (settings.overlayAutoHideMillis > 0) {
                    mainHandler.postDelayed(hideRunnable, settings.overlayAutoHideMillis)
                }
            }
        }

        if (settings.voiceEnabled) voice?.announce(analysis, settings)
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
