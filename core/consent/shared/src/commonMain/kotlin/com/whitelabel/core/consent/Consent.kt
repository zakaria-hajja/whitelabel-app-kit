package com.whitelabel.core.consent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's consent state. With no [port] (an app without Firebase or ads) nothing that needs consent
 * may run, so it resolves to everything denied.
 */
class Consent(private val port: ConsentPort?) {
    private val _state = MutableStateFlow<ConsentState>(ConsentState.Unknown)
    val state: StateFlow<ConsentState> = _state.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    // An atomic compare-and-set is enough; no lock is needed for a once-per-launch guard.
    private val started = MutableStateFlow(false)

    /** Gathers consent once per instance (one per launch); later calls do nothing. */
    fun gatherOnLaunch() {
        if (!started.compareAndSet(expect = false, update = true)) return
        val port = port
        if (port == null) {
            _state.value = ConsentState.Resolved(canRequestAds = false, analyticsAllowed = false)
            return
        }
        port.gather { result ->
            _state.value = result?.let(::consentStateOf) ?: ConsentState.Failed
            _privacyOptionsRequired.value = port.privacyOptionsRequired()
        }
    }

    /** Shows UMP's privacy options; a changed choice (a withdrawal too) applies as soon as the form closes. */
    fun showPrivacyOptions() {
        val port = port ?: return
        port.showPrivacyOptions { result ->
            if (result != null) _state.value = consentStateOf(result)
            _privacyOptionsRequired.value = port.privacyOptionsRequired()
        }
    }
}
