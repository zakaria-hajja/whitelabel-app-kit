package com.whitelabel.feature.settings.presentation

/**
 * The primary language of a BCP 47 tag (`fr-CA` → `fr`, `zh-Hant-TW` → `zh`; `en_US` too),
 * lowercased, or `null` for an empty or missing tag (follow the device).
 */
internal fun primaryLanguage(tag: String?): String? =
    tag?.substringBefore('-')?.substringBefore('_')?.lowercase()?.takeIf { it.isNotEmpty() }
