package com.ridescore.app.data.log

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.ridescore.app.domain.log.OfferCsv
import com.ridescore.app.domain.model.ScreenAnalysis
import com.ridescore.app.domain.settings.RideScoreSettings
import java.io.File

/** What the ride log currently holds, for the settings screen. */
data class LogStats(
    val rows: Int = 0,
    val bytes: Long = 0,
    val firstEntry: String? = null,
    val lastEntry: String? = null,
) {
    val isEmpty: Boolean get() = rows <= 0
}

/**
 * Keeps a local CSV of every offer seen, so patterns can be looked at later.
 *
 * Design constraints, in order of importance:
 *
 *  - **Off unless the driver turns it on.** This is the only part of RideScore
 *    that writes ride data to disk, so it is opt-in rather than something that
 *    quietly starts happening.
 *  - **It stays on the phone.** The file lives in the app's private storage,
 *    which no other app can read, and it leaves only when the driver shares it
 *    themselves through Android's share sheet. Nothing uploads it.
 *  - **It never grows without bound.** A driver taking offers all day would
 *    otherwise fill their storage; the file is trimmed to its most recent rows
 *    once it passes the size cap.
 *  - **It never logs the same offer twice.** An offer screen repaints many
 *    times a second, so identical offers seen again within a few minutes are
 *    the same offer, not a new one.
 */
class OfferLogger(private val context: Context) {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    private val recentlyLogged = LinkedHashMap<String, Long>()

    fun log(analysis: ScreenAnalysis, settings: RideScoreSettings, nowMillis: Long = System.currentTimeMillis()) {
        if (analysis.ranked.isEmpty()) return

        val screenId = analysis.signature
        if (isDuplicate(screenId, nowMillis)) return

        val rows = OfferCsv.rows(analysis, settings, screenId, nowMillis)
        if (rows.isEmpty()) return

        runCatching {
            val target = file
            val isNew = !target.exists() || target.length() == 0L
            target.appendText(
                buildString {
                    if (isNew) append(OfferCsv.header()).append('\n')
                    rows.forEach { append(it).append('\n') }
                },
            )
            if (target.length() > MAX_BYTES) trim(target)
        }.onFailure { Log.w(TAG, "Could not write the ride log", it) }
    }

    fun stats(): LogStats {
        val target = file
        if (!target.exists()) return LogStats()
        return runCatching {
            val lines = target.readLines()
            val dataRows = lines.drop(1).filter { it.isNotBlank() }
            LogStats(
                rows = dataRows.size,
                bytes = target.length(),
                firstEntry = dataRows.firstOrNull()?.substringBefore(','),
                lastEntry = dataRows.lastOrNull()?.substringBefore(','),
            )
        }.getOrElse { LogStats() }
    }

    fun clear() {
        recentlyLogged.clear()
        runCatching { file.delete() }
    }

    /**
     * An intent the driver can use to send the log wherever they like. The file
     * is exposed through a FileProvider for the moment it takes to share it, and
     * by nothing else.
     */
    fun shareIntent(): Intent? {
        val target = file
        if (!target.exists() || target.length() == 0L) return null

        val uri: Uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        }.getOrElse {
            Log.w(TAG, "Could not expose the ride log", it)
            return null
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "RideScore ride log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun isDuplicate(screenId: String, nowMillis: Long): Boolean {
        val previous = recentlyLogged[screenId]
        if (previous != null && nowMillis - previous < DUPLICATE_WINDOW_MS) return true

        recentlyLogged[screenId] = nowMillis
        if (recentlyLogged.size > MAX_REMEMBERED) {
            val oldest = recentlyLogged.keys.firstOrNull()
            if (oldest != null) recentlyLogged.remove(oldest)
        }
        return false
    }

    /** Keeps the newest half when the file gets too big, header intact. */
    private fun trim(target: File) {
        runCatching {
            val lines = target.readLines()
            if (lines.size < 4) return
            val header = lines.first()
            val kept = lines.drop(1).takeLast((lines.size - 1) / 2)
            target.writeText((listOf(header) + kept).joinToString("\n", postfix = "\n"))
        }.onFailure { Log.w(TAG, "Could not trim the ride log", it) }
    }

    private companion object {
        const val TAG = "RideScoreLog"
        const val FILE_NAME = "ride-log.csv"

        /** Roughly 8,000 offers. Well past a month of full-time driving. */
        const val MAX_BYTES = 2L * 1024 * 1024

        const val DUPLICATE_WINDOW_MS = 5 * 60 * 1000L
        const val MAX_REMEMBERED = 200
    }
}
