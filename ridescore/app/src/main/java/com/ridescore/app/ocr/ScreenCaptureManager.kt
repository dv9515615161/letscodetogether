package com.ridescore.app.ocr

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Holds the screen-capture session created from the driver's explicit
 * MediaProjection consent.
 *
 * Android shows its own cast/record indicator the whole time this is alive, and
 * the consent has to be granted by hand - there is no way around that, and
 * RideScore does not try. Frames are pulled only when the OCR fallback asks for
 * one; nothing is recorded, stored or sent anywhere.
 */
object ScreenCaptureManager {

    private const val TAG = "RideScoreCapture"

    /** Nothing above this width is worth OCR-ing; scaling down is faster. */
    private const val MAX_CAPTURE_WIDTH = 1080

    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var width = 0
    private var height = 0

    private val handler = Handler(Looper.getMainLooper())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "Screen capture stopped by the system or the user")
            stop()
        }
    }

    val isReady: Boolean get() = imageReader != null && projection != null

    fun start(mediaProjection: MediaProjection, screenWidth: Int, screenHeight: Int, densityDpi: Int) {
        stop()
        if (screenWidth <= 0 || screenHeight <= 0) return

        val scale = if (screenWidth > MAX_CAPTURE_WIDTH) MAX_CAPTURE_WIDTH.toFloat() / screenWidth else 1f
        width = (screenWidth * scale).toInt()
        height = (screenHeight * scale).toInt()

        projection = mediaProjection
        mediaProjection.registerCallback(projectionCallback, handler)

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "RideScoreCapture",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
        )
        Log.i(TAG, "Screen capture ready at ${width}x$height")
    }

    fun stop() {
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        runCatching {
            projection?.unregisterCallback(projectionCallback)
            projection?.stop()
        }
        virtualDisplay = null
        imageReader = null
        projection = null
    }

    /** The most recent frame, or null when none is available yet. */
    fun captureBitmap(): Bitmap? {
        val reader = imageReader ?: return null
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return null
        return try {
            val plane = image.planes[0]
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val padded = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888,
            )
            padded.copyPixelsFromBuffer(plane.buffer)
            if (padded.width != width) {
                val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
                padded.recycle()
                cropped
            } else {
                padded
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame conversion failed", e)
            null
        } finally {
            image.close()
        }
    }
}
