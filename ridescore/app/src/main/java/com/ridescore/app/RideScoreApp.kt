package com.ridescore.app

import android.app.Application
import com.ridescore.app.data.settings.SettingsCache
import com.ridescore.app.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Warms [SettingsCache] as early as possible so the accessibility service has
 * the driver's real thresholds - not the defaults - by the time the first offer
 * appears.
 */
class RideScoreApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val repository = SettingsRepository(this)
        scope.launch { repository.settings.collect { /* SettingsCache is updated by the flow */ } }
    }
}
