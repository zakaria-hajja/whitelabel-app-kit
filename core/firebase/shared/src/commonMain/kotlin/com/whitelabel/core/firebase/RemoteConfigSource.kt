package com.whitelabel.core.firebase

/** The raw, platform-implemented remote-config store. Callback-based so Swift can implement it. */
interface RemoteConfigSource {
    /** The activated value for [key]; null when absent. */
    fun raw(key: String): String?

    /** Fetches and activates. Never throws: [onDone] gets `false` on error or when nothing changed. */
    fun fetchAndActivate(onDone: (changed: Boolean) -> Unit)
}
