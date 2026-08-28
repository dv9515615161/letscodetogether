package com.ridescore.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory status for the app's own home screen.
 *
 * Nothing here is persisted or sent anywhere; it exists so a driver can tell
 * whether RideScore is actually seeing their driver app, and so a developer can
 * see how the parse went without attaching a debugger.
 */
object Diagnostics {

    data class State(
        val serviceConnected: Boolean = false,
        val lastForegroundPackage: String? = null,
        val lastForegroundSupported: Boolean = false,
        val lastOfferCount: Int = 0,
        val lastAnalysisMicros: Long = 0,
        val lastDecision: String? = null,
        val lastConfidence: Float = 0f,
        val lastUpdatedAtMillis: Long = 0,
        val overlayPermissionMissing: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(transform: (State) -> State) {
        _state.value = transform(_state.value)
    }
}
