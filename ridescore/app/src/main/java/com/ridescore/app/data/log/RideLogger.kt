package com.ridescore.app.data.log

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.ridescore.app.domain.log.RideCsv
import com.ridescore.app.domain.receipt.RideReceipt
import java.io.File

/**
 * A local CSV of rides that actually finished, read off the order-details
 * screen each app shows afterwards.
 *
 * Same rules as the offer log: opt-in with it, private to the app, capped in
 * size, and it leaves the phone only when the driver shares it themselves.
 *
 * Where it differs is what it is for. The offer log records predictions. This
 * records outcomes - the minutes a ride really took, the kilometres really
 * covered, the rupees that really arrived - and the two together are the only
 * honest way to check whether the app's advice was any good.
 */
class RideLogger(private val context: Context) {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    private val recentlyLogged = LinkedHashMap<String, Long>()

    fun log(receipt: RideReceipt, nowMillis: Long = System.currentTimeMillis()) {
        // A receipt sits on screen while the driver reads it, repainting as
        // they scroll. One ride, one row.
        if (isDuplicate(receipt.signature, nowMillis)) return

        runCatching {
            val target = file
            rotateIfSchemaChanged(target)
            val isNew = !target.exists() || target.length() == 0L
            target.appendText(
                buildString {
                    if (isNew) append(RideCsv.header()).append('\n')
                    append(RideCsv.row(receipt, nowMillis)).append('\n')
                },
            )
            if (target.length() > MAX_BYTES) trim(target)
        }.onFailure { Log.w(TAG, "Could not write the completed-ride log", it) }
    }

    fun stats(): LogStats {
        val target = file
        if (!target.exists()) return LogStats()
        return runCatching {
            val rows = target.readLines().drop(1).filter { it.isNotBlank() }
            LogStats(
                rows = rows.size,
                bytes = target.length(),
                firstEntry = rows.firstOrNull()?.substringBefore(','),
                lastEntry = rows.lastOrNull()?.substringBefore(','),
            )
        }.getOrElse { LogStats() }
    }

    fun clear() {
        recentlyLogged.clear()
        runCatching { file.delete() }
        runCatching { File(context.filesDir, PREVIOUS_FILE_NAME).delete() }
    }

    fun shareIntent(): Intent? {
        val target = file
        if (!target.exists() || target.length() == 0L) return null

        val uri: Uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        }.getOrElse {
            Log.w(TAG, "Could not expose the completed-ride log", it)
            return null
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "RideScore completed rides")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun isDuplicate(signature: String, nowMillis: Long): Boolean {
        val previous = recentlyLogged[signature]
        if (previous != null && nowMillis - previous < DUPLICATE_WINDOW_MS) return true

        recentlyLogged[signature] = nowMillis
        if (recentlyLogged.size > MAX_REMEMBERED) {
            recentlyLogged.keys.firstOrNull()?.let { recentlyLogged.remove(it) }
        }
        return false
    }

    private fun rotateIfSchemaChanged(target: File) {
        if (!target.exists() || target.length() == 0L) return
        val header = runCatching { target.bufferedReader().use { it.readLine() } }.getOrNull()
        if (header == RideCsv.header()) return

        runCatching {
            val previous = File(context.filesDir, PREVIOUS_FILE_NAME)
            previous.delete()
            target.renameTo(previous)
        }
    }

    private fun trim(target: File) {
        runCatching {
            val lines = target.readLines()
            if (lines.size < 2) return
            val kept = lines.drop(1).takeLast(KEEP_ROWS)
            target.writeText((listOf(RideCsv.header()) + kept).joinToString("\n", postfix = "\n"))
        }
    }

    private companion object {
        const val TAG = "RideScore"
        const val FILE_NAME = "rides.csv"
        const val PREVIOUS_FILE_NAME = "rides-previous.csv"

        /** A ride a minute all day would still take months to reach this. */
        const val MAX_BYTES = 1_000_000L
        const val KEEP_ROWS = 4_000

        /** A receipt on screen for longer than this is a second look, not a second ride. */
        const val DUPLICATE_WINDOW_MS = 30 * 60_000L
        const val MAX_REMEMBERED = 200
    }
}
