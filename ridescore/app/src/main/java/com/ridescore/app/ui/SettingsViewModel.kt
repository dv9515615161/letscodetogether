package com.ridescore.app.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ridescore.app.data.settings.SettingsCache
import com.ridescore.app.data.settings.SettingsRepository
import com.ridescore.app.domain.settings.RideScoreSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<RideScoreSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsCache.current)

    fun update(transform: (RideScoreSettings) -> RideScoreSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun resetToDefaults() {
        viewModelScope.launch { repository.resetToDefaults() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as Application
                SettingsViewModel(SettingsRepository(application))
            }
        }
    }
}
