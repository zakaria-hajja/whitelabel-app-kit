package com.whitelabel.core.domain

/** The app's own UI language, separate from the device's. */
interface LanguageRepository {
    /** The chosen BCP 47 language tag (e.g. `fr`), or `null` to follow the device. */
    fun current(): String?

    /** Stores [languageTag] and applies it ([appliesImmediately]); `null` follows the device again. */
    fun set(languageTag: String?)

    /**
     * Whether [set] applies at once (Android recreates the screen) or at the next launch (iOS reads
     * the app's language only when it starts). Swift implementations must state it.
     */
    val appliesImmediately: Boolean get() = true
}
