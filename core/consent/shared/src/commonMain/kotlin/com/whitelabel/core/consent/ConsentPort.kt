package com.whitelabel.core.consent

/** Implemented per platform (Google UMP). Callbacks only, so Swift can implement it. */
interface ConsentPort {
    /** Shows the form only if UMP requires it. Calls back with null on error. */
    fun gather(onDone: (UmpResult?) -> Unit)
    fun privacyOptionsRequired(): Boolean
    fun showPrivacyOptions(onDone: () -> Unit)
}
