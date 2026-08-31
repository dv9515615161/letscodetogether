package com.ridescore.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ridescore.app.domain.settings.AppMode
import com.ridescore.app.domain.settings.EarningsPlan
import com.ridescore.app.domain.settings.OverlayMode
import com.ridescore.app.domain.settings.RideScoreSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ridescore_settings")

/**
 * Persists the driver's settings.
 *
 * Everything lives in a local DataStore file. Nothing is uploaded, and there is
 * no account, no sync and no analytics.
 *
 * The accessibility service cannot suspend while an offer is on screen, so the
 * latest values are also mirrored into a plain volatile field
 * ([SettingsCache]) that any thread can read in nanoseconds.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<RideScoreSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs.toSettings().also { SettingsCache.update(it) } }

    suspend fun update(transform: (RideScoreSettings) -> RideScoreSettings) {
        context.dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings()).sanitised()
            prefs.write(updated)
            SettingsCache.update(updated)
        }
    }

    suspend fun resetToDefaults() = update { RideScoreSettings.DEFAULT }

    private fun Preferences.toSettings(): RideScoreSettings {
        val d = RideScoreSettings.DEFAULT
        return RideScoreSettings(
            vehicleName = this[Keys.VEHICLE] ?: d.vehicleName,
            mileageKmPerLitre = this[Keys.MILEAGE] ?: d.mileageKmPerLitre,
            petrolPricePerLitre = this[Keys.PETROL] ?: d.petrolPricePerLitre,
            acceptNetPerHour = this[Keys.ACCEPT_PER_HOUR] ?: d.acceptNetPerHour,
            maybeNetPerHour = this[Keys.MAYBE_PER_HOUR] ?: d.maybeNetPerHour,
            minNetPerKm = this[Keys.MIN_PER_KM] ?: d.minNetPerKm,
            requireBothMetrics = this[Keys.REQUIRE_BOTH] ?: d.requireBothMetrics,
            pickupSpeedKmph = this[Keys.PICKUP_SPEED] ?: d.pickupSpeedKmph,
            includePickupDistance = this[Keys.INCLUDE_PICKUP_KM] ?: d.includePickupDistance,
            includePickupTime = this[Keys.INCLUDE_PICKUP_MIN] ?: d.includePickupTime,
            incentiveEnabled = this[Keys.INCENTIVE_ON] ?: d.incentiveEnabled,
            incentiveBonus = this[Keys.INCENTIVE_BONUS] ?: d.incentiveBonus,
            incentiveTripsTarget = this[Keys.INCENTIVE_TARGET] ?: d.incentiveTripsTarget,
            incentiveTripsDone = this[Keys.INCENTIVE_DONE] ?: d.incentiveTripsDone,
            emptyReturnEnabled = this[Keys.EMPTY_RETURN_ON] ?: d.emptyReturnEnabled,
            emptyReturnFromKm = this[Keys.EMPTY_RETURN_FROM_KM] ?: d.emptyReturnFromKm,
            emptyReturnFraction = this[Keys.EMPTY_RETURN_FRACTION] ?: d.emptyReturnFraction,
            maintenanceEnabled = this[Keys.MAINTENANCE_ON] ?: d.maintenanceEnabled,
            maintenancePerKm = this[Keys.MAINTENANCE_PER_KM] ?: d.maintenancePerKm,
            earningsPlan = this[Keys.EARNINGS_PLAN]
                ?.let { runCatching { EarningsPlan.valueOf(it) }.getOrNull() } ?: d.earningsPlan,
            commissionPercent = this[Keys.COMMISSION_PCT] ?: d.commissionPercent,
            gstOnCommissionPercent = this[Keys.GST_ON_COMMISSION_PCT] ?: d.gstOnCommissionPercent,
            taxesAndFeesPercent = this[Keys.TAXES_PCT] ?: d.taxesAndFeesPercent,
            perOrderFee = this[Keys.PER_ORDER_FEE] ?: d.perOrderFee,
            parcelOrdersExempt = this[Keys.PARCEL_EXEMPT] ?: d.parcelOrdersExempt,
            dailyPlanFee = this[Keys.DAILY_PLAN_FEE] ?: d.dailyPlanFee,
            overlayEnabled = this[Keys.OVERLAY_ON] ?: d.overlayEnabled,
            overlayMode = this[Keys.OVERLAY_MODE]?.let { runCatching { OverlayMode.valueOf(it) }.getOrNull() }
                ?: d.overlayMode,
            overlayShowDetailsInQuickMode = this[Keys.QUICK_DETAILS] ?: d.overlayShowDetailsInQuickMode,
            overlayTextScale = this[Keys.TEXT_SCALE] ?: d.overlayTextScale,
            voiceEnabled = this[Keys.VOICE_ON] ?: d.voiceEnabled,
            voiceMinIntervalMillis = this[Keys.VOICE_INTERVAL] ?: d.voiceMinIntervalMillis,
            overlayAutoHideMillis = this[Keys.OVERLAY_AUTO_HIDE] ?: d.overlayAutoHideMillis,
            lockScreenNoticeEnabled = this[Keys.LOCK_SCREEN_NOTICE] ?: d.lockScreenNoticeEnabled,
            appMode = this[Keys.APP_MODE]?.let { runCatching { AppMode.valueOf(it) }.getOrNull() } ?: d.appMode,
            ocrFallbackEnabled = this[Keys.OCR_ON] ?: d.ocrFallbackEnabled,
            offerLogEnabled = this[Keys.OFFER_LOG] ?: d.offerLogEnabled,
            disclosureAccepted = this[Keys.DISCLOSURE] ?: d.disclosureAccepted,
            setupCompleted = this[Keys.SETUP_DONE] ?: d.setupCompleted,
            lowConfidenceThreshold = this[Keys.LOW_CONFIDENCE] ?: d.lowConfidenceThreshold,
            minUsableConfidence = this[Keys.MIN_CONFIDENCE] ?: d.minUsableConfidence,
            preferredDestinations = this[Keys.PREFERRED_DESTINATIONS]
                ?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: d.preferredDestinations,
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.write(s: RideScoreSettings) {
        this[Keys.VEHICLE] = s.vehicleName
        this[Keys.MILEAGE] = s.mileageKmPerLitre
        this[Keys.PETROL] = s.petrolPricePerLitre
        this[Keys.ACCEPT_PER_HOUR] = s.acceptNetPerHour
        this[Keys.MAYBE_PER_HOUR] = s.maybeNetPerHour
        this[Keys.MIN_PER_KM] = s.minNetPerKm
        this[Keys.REQUIRE_BOTH] = s.requireBothMetrics
        this[Keys.PICKUP_SPEED] = s.pickupSpeedKmph
        this[Keys.INCLUDE_PICKUP_KM] = s.includePickupDistance
        this[Keys.INCLUDE_PICKUP_MIN] = s.includePickupTime
        this[Keys.INCENTIVE_ON] = s.incentiveEnabled
        this[Keys.INCENTIVE_BONUS] = s.incentiveBonus
        this[Keys.INCENTIVE_TARGET] = s.incentiveTripsTarget
        this[Keys.INCENTIVE_DONE] = s.incentiveTripsDone
        this[Keys.EMPTY_RETURN_ON] = s.emptyReturnEnabled
        this[Keys.EMPTY_RETURN_FROM_KM] = s.emptyReturnFromKm
        this[Keys.EMPTY_RETURN_FRACTION] = s.emptyReturnFraction
        this[Keys.MAINTENANCE_ON] = s.maintenanceEnabled
        this[Keys.MAINTENANCE_PER_KM] = s.maintenancePerKm
        this[Keys.EARNINGS_PLAN] = s.earningsPlan.name
        this[Keys.COMMISSION_PCT] = s.commissionPercent
        this[Keys.GST_ON_COMMISSION_PCT] = s.gstOnCommissionPercent
        this[Keys.TAXES_PCT] = s.taxesAndFeesPercent
        this[Keys.PER_ORDER_FEE] = s.perOrderFee
        this[Keys.PARCEL_EXEMPT] = s.parcelOrdersExempt
        this[Keys.DAILY_PLAN_FEE] = s.dailyPlanFee
        this[Keys.OVERLAY_ON] = s.overlayEnabled
        this[Keys.OVERLAY_MODE] = s.overlayMode.name
        this[Keys.QUICK_DETAILS] = s.overlayShowDetailsInQuickMode
        this[Keys.TEXT_SCALE] = s.overlayTextScale
        this[Keys.VOICE_ON] = s.voiceEnabled
        this[Keys.VOICE_INTERVAL] = s.voiceMinIntervalMillis
        this[Keys.OVERLAY_AUTO_HIDE] = s.overlayAutoHideMillis
        this[Keys.LOCK_SCREEN_NOTICE] = s.lockScreenNoticeEnabled
        this[Keys.APP_MODE] = s.appMode.name
        this[Keys.OCR_ON] = s.ocrFallbackEnabled
        this[Keys.OFFER_LOG] = s.offerLogEnabled
        this[Keys.DISCLOSURE] = s.disclosureAccepted
        this[Keys.SETUP_DONE] = s.setupCompleted
        this[Keys.LOW_CONFIDENCE] = s.lowConfidenceThreshold
        this[Keys.MIN_CONFIDENCE] = s.minUsableConfidence
        this[Keys.PREFERRED_DESTINATIONS] = s.preferredDestinations.joinToString("\n")
    }

    private object Keys {
        val VEHICLE = stringPreferencesKey("vehicle")
        val MILEAGE = doublePreferencesKey("mileage_kmpl")
        val PETROL = doublePreferencesKey("petrol_price")
        val ACCEPT_PER_HOUR = doublePreferencesKey("accept_net_per_hour")
        val MAYBE_PER_HOUR = doublePreferencesKey("maybe_net_per_hour")
        val MIN_PER_KM = doublePreferencesKey("min_net_per_km")
        val REQUIRE_BOTH = booleanPreferencesKey("require_both_metrics")
        val PICKUP_SPEED = doublePreferencesKey("pickup_speed_kmph")
        val INCLUDE_PICKUP_KM = booleanPreferencesKey("include_pickup_distance")
        val INCLUDE_PICKUP_MIN = booleanPreferencesKey("include_pickup_time")
        val INCENTIVE_ON = booleanPreferencesKey("incentive_enabled")
        val INCENTIVE_BONUS = doublePreferencesKey("incentive_bonus")
        val INCENTIVE_TARGET = intPreferencesKey("incentive_trips_target")
        val INCENTIVE_DONE = intPreferencesKey("incentive_trips_done")
        val EMPTY_RETURN_ON = booleanPreferencesKey("empty_return_enabled")
        val EMPTY_RETURN_FROM_KM = doublePreferencesKey("empty_return_from_km")
        val EMPTY_RETURN_FRACTION = doublePreferencesKey("empty_return_fraction")
        val TEXT_SCALE = floatPreferencesKey("overlay_text_scale")
        val MAINTENANCE_ON = booleanPreferencesKey("maintenance_enabled")
        val MAINTENANCE_PER_KM = doublePreferencesKey("maintenance_per_km")
        val EARNINGS_PLAN = stringPreferencesKey("earnings_plan")
        val COMMISSION_PCT = doublePreferencesKey("commission_percent")
        val GST_ON_COMMISSION_PCT = doublePreferencesKey("gst_on_commission_percent")
        val TAXES_PCT = doublePreferencesKey("taxes_and_fees_percent")
        val PER_ORDER_FEE = doublePreferencesKey("per_order_fee")
        val PARCEL_EXEMPT = booleanPreferencesKey("parcel_orders_exempt")
        val DAILY_PLAN_FEE = doublePreferencesKey("daily_plan_fee")
        val OVERLAY_ON = booleanPreferencesKey("overlay_enabled")
        val OVERLAY_MODE = stringPreferencesKey("overlay_mode")
        val QUICK_DETAILS = booleanPreferencesKey("quick_mode_details")
        val VOICE_ON = booleanPreferencesKey("voice_enabled")
        val VOICE_INTERVAL = longPreferencesKey("voice_min_interval")
        val OVERLAY_AUTO_HIDE = longPreferencesKey("overlay_auto_hide")
        val LOCK_SCREEN_NOTICE = booleanPreferencesKey("lock_screen_notice")
        val APP_MODE = stringPreferencesKey("app_mode")
        val OCR_ON = booleanPreferencesKey("ocr_fallback_enabled")
        val OFFER_LOG = booleanPreferencesKey("offer_log_enabled")
        val DISCLOSURE = booleanPreferencesKey("disclosure_accepted")
        val SETUP_DONE = booleanPreferencesKey("setup_completed")
        val LOW_CONFIDENCE = floatPreferencesKey("low_confidence_threshold")
        val MIN_CONFIDENCE = floatPreferencesKey("min_usable_confidence")
        val PREFERRED_DESTINATIONS = stringPreferencesKey("preferred_destinations")
    }
}

/**
 * The newest settings, readable without suspending.
 *
 * The accessibility service reads this on every offer; a DataStore read there
 * would add milliseconds to the one path that has to be instant.
 */
object SettingsCache {

    @Volatile
    var current: RideScoreSettings = RideScoreSettings.DEFAULT
        private set

    /** Bumped on every change so caches keyed on settings can be dropped. */
    @Volatile
    var version: Long = 0L
        private set

    fun update(settings: RideScoreSettings) {
        if (settings != current) {
            current = settings
            version += 1
        }
    }
}
