package com.whitelabel.core.domain

/** The app's own UI language, separate from the device's. */
interface LanguageRepository {
    /** The chosen BCP 47 language tag (e.g. `fr`), or `null` to follow the device. */
    fun current(): String?

    /** Applies [languageTag] at once (the screen is recreated); `null` follows the device again. */
    fun set(languageTag: String?)
}
