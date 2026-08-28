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
        /** The last offer arrived while the phone was locked, where no app may draw a card. */
        val overlayBlockedByLockScreen: Boolean = false,
        /**
         * The text of the last supported screen, and how it was split into
         * offer cards. Held only in memory, only for the app's own diagnostics
         * screen, and never written to disk or sent anywhere - it is there so a
         * driver can show a developer what their screen actually looked like
         * when a parse went wrong.
         */
        val lastLines: List<String> = emptyList(),
        val lastBlocks: List<List<String>> = emptyList(),
        val lastOfferSummaries: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(transform: (State) -> State) {
        _state.value = transform(_state.value)
    }
}
