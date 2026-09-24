package com.ridescore.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ridescore.app.R
import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.model.ScreenAnalysis
import com.ridescore.app.domain.settings.RideScoreSettings

/**
 * The verdict as a notification, for when the card cannot be drawn.
 *
 * Android hides overlay windows behind the lock screen for every app, so an
 * offer that arrives while the phone is locked - which is most of them, on a
 * phone in a handlebar mount - would otherwise get no advice at all. A
 * notification is the one channel that does reach the lock screen, and it is a
 * plain, ordinary notification: no full-screen intent, nothing that grabs the
 * screen away from the offer the driver is looking at.
 *
 * It is silent on purpose. Rapido is already making noise, and the voice
 * announcement covers the ears; this is only there to be glanceable.
 */
class DecisionNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)
    private var lastSignature: String? = null

    fun notify(analysis: ScreenAnalysis, settings: RideScoreSettings) {
        val content = OverlayPresenter.present(analysis, settings) ?: return
        if (!manager.areNotificationsEnabled()) return

        val signature = content.header + content.primary
        if (signature == lastSignature) return
        lastSignature = signature

        ensureChannel()

        val detail = buildList {
            addAll(content.detailLines)
            if (content.otherOffers.isNotEmpty()) add(content.otherOffers.first())
        }.joinToString(" · ")

        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${content.header} · ${content.primary}")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            // Readable on the lock screen: that is the whole point of it.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(accent(content.decision))
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(settings.overlayAutoHideMillis.coerceAtLeast(8_000L))
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun cancel() {
        lastSignature = null
        runCatching { manager.cancel(NOTIFICATION_ID) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.decision_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.decision_channel_description)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun accent(decision: Decision): Int = when (decision) {
        Decision.ACCEPT -> 0xFF1E9E52.toInt()
        Decision.MAYBE -> 0xFFD79A00.toInt()
        Decision.REJECT -> 0xFFD64545.toInt()
        Decision.CHECK -> 0xFF7A7A7A.toInt()
    }

    private companion object {
        const val CHANNEL_ID = "ridescore_decision"
        const val NOTIFICATION_ID = 4212
    }
}
