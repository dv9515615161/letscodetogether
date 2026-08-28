package com.ridescore.app.ocr

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ridescore.app.domain.model.ScreenSnapshot
import com.ridescore.app.domain.model.SourceApp
import com.ridescore.app.domain.model.TextBlock
import com.ridescore.app.domain.model.TextSource
import com.ridescore.app.engine.OcrFallbackProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The fallback path: read the pixels when the accessibility tree did not give
 * us what we need.
 *
 * It is deliberately hard to trigger. It runs only when the driver has switched
 * the fallback on, only when a supported app is in front, only when the
 * accessibility parse came back unusable, and never more than once every
 * [MIN_INTERVAL_MS]. On a normal offer screen, where the text is exposed
 * properly, this class never does any work at all.
 *
 * Recognition is ML Kit's on-device Latin model - a bundled model, no network
 * call, nothing leaves the phone.
 */
class MlKitOcrProvider : OcrFallbackProvider {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private var lastRunUptime = 0L

    override fun isAvailable(): Boolean = ScreenCaptureManager.isReady

    override suspend fun capture(packageName: String): ScreenSnapshot? {
        val now = SystemClock.uptimeMillis()
        if (now - lastRunUptime < MIN_INTERVAL_MS) return null
        lastRunUptime = now

        val bitmap = ScreenCaptureManager.captureBitmap() ?: return null
        return try {
            recognise(bitmap, packageName)
        } catch (e: Exception) {
            Log.w(TAG, "OCR failed", e)
            null
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun recognise(bitmap: Bitmap, packageName: String): ScreenSnapshot? {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()

        // One ML Kit block is usually one visual card, which is exactly the
        // grouping the offer segmenter wants.
        val blocks = result.textBlocks
            .sortedBy { it.boundingBox?.top ?: 0 }
            .map { block ->
                TextBlock(
                    // A screen capture includes RideScore's own card, which
                    // sits on top of the offer. Reading our own output back in
                    // as screen text would be circular, so drop it.
                    lines = block.lines.map { it.text }.filterNot(::isOwnCardText),
                    top = block.boundingBox?.top ?: 0,
                )
            }
            .filter { it.lines.isNotEmpty() }

        if (blocks.isEmpty()) return null

        return ScreenSnapshot(
            packageName = packageName,
            sourceApp = SourceApp.fromPackage(packageName),
            blocks = blocks,
            allLines = blocks.flatMap { it.lines },
            capturedAtMillis = System.currentTimeMillis(),
            textSource = TextSource.OCR,
        )
    }

    private fun isOwnCardText(line: String): Boolean {
        val n = line.lowercase()
        return OWN_CARD_MARKERS.any { n.contains(it) }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { value -> if (cont.isActive) cont.resume(value) }
        addOnFailureListener { error -> if (cont.isActive) cont.resumeWithException(error) }
        addOnCanceledListener { cont.cancel() }
    }

    private companion object {
        /** Phrases that only ever appear on RideScore's own advisory card. */
        val OWN_CARD_MARKERS = listOf(
            "net/hr", "net/km", "could not read", "no good order", "best of ",
            "advisory", "low confidence", "/km target",
        )

        const val TAG = "RideScoreOcr"

        /** Never more than one screen capture per second and a bit. */
        const val MIN_INTERVAL_MS = 1_200L
    }
}
