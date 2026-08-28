package com.ridescore.app.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ridescore.app.data.log.LogStats
import com.ridescore.app.data.log.OfferLogger
import com.ridescore.app.data.settings.SettingsCache
import com.ridescore.app.data.settings.SettingsRepository
import com.ridescore.app.domain.settings.RideScoreSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val offerLog: OfferLogger,
) : ViewModel() {

    private val _logStats = MutableStateFlow(LogStats())
    val logStats: StateFlow<LogStats> = _logStats.asStateFlow()

    val settings: StateFlow<RideScoreSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsCache.current)

    fun update(transform: (RideScoreSettings) -> RideScoreSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun resetToDefaults() {
        viewModelScope.launch { repository.resetToDefaults() }
    }

    fun refreshLogStats() {
        // Reading and counting the file is disk work; keep it off the main thread.
        viewModelScope.launch {
            _logStats.value = withContext(Dispatchers.IO) { offerLog.stats() }
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { offerLog.clear() }
            refreshLogStats()
        }
    }

    suspend fun shareLogIntent(): Intent? = withContext(Dispatchers.IO) { offerLog.shareIntent() }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as Application
                SettingsViewModel(SettingsRepository(application), OfferLogger(application))
            }
        }
    }
}
