package com.whitelabel.core.data.config

import com.whitelabel.core.branding.RuntimeDefaults
import com.whitelabel.core.domain.AppConfig
import com.whitelabel.core.domain.ThemePreference
import kotlinx.serialization.Serializable

/**
 * The parts of `PublicAppConfig` the app uses. Enum-like values are plain strings, so a value
 * added by a newer backend never makes the whole config unreadable.
 */
@Serializable
internal data class AppConfigDto(
    val features: Map<String, Boolean> = emptyMap(),
    val support: SupportDto? = null,
    val legal: LegalDto? = null,
    val runtime: RuntimeDto? = null,
)

@Serializable
internal data class SupportDto(val email: String? = null)

@Serializable
internal data class LegalDto(val privacyPolicyUrl: String? = null, val termsUrl: String? = null)

@Serializable
internal data class RuntimeDto(val defaultTheme: String? = null, val supportedLanguages: List<String> = emptyList())

internal fun RuntimeDefaults.toAppConfig() = AppConfig(
    features = emptyMap(),
    supportEmail = supportEmail,
    privacyPolicyUrl = privacyPolicyUrl,
    termsUrl = termsUrl,
    defaultTheme = ThemePreference.SYSTEM,
    supportedLanguages = emptyList(),
)

/** Remote values win; anything the backend left out keeps the build-time default. */
internal fun AppConfigDto.toAppConfig(defaults: RuntimeDefaults) = AppConfig(
    features = features,
    supportEmail = support?.email ?: defaults.supportEmail,
    privacyPolicyUrl = legal?.privacyPolicyUrl ?: defaults.privacyPolicyUrl,
    termsUrl = legal?.termsUrl ?: defaults.termsUrl,
    defaultTheme = ThemePreference.entries.firstOrNull { it.name == runtime?.defaultTheme } ?: ThemePreference.SYSTEM,
    supportedLanguages = runtime?.supportedLanguages.orEmpty(),
)
