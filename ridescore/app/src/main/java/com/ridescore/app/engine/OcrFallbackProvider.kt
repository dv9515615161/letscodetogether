package com.ridescore.app.engine

import com.ridescore.app.domain.model.ScreenSnapshot

/**
 * Supplies a screen snapshot built from pixels instead of accessibility nodes.
 *
 * Declared here, in pure Kotlin, so the pipeline can be tested with a fake and
 * so the OCR machinery (MediaProjection + ML Kit) stays optional at runtime.
 */
interface OcrFallbackProvider {

    /** True when a capture could actually happen right now. */
    fun isAvailable(): Boolean

    /**
     * Captures the current screen and recognises text on it. Returns null when
     * capture is unavailable, throttled, or produced nothing useful.
     */
    suspend fun capture(packageName: String): ScreenSnapshot?
}
