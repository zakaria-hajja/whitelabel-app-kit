package com.whitelabel.core.consent

/** Implemented per platform (Google UMP). Callbacks only, so Swift can implement it. */
interface ConsentPort {
    /** Shows the form only if UMP requires it. Calls back with null on error. */
    fun gather(onDone: (UmpResult?) -> Unit)
    fun privacyOptionsRequired(): Boolean
    /**
     * Shows UMP's privacy options form. Calls back, once it closes, with the consent as UMP now has it,
     * so a withdrawal applies at once; null (no screen to show it on, or an error) keeps the current state.
     */
    fun showPrivacyOptions(onDone: (UmpResult?) -> Unit)
}
