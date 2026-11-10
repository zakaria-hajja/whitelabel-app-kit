package com.whitelabel.core.domain

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Runtime configuration from the backend (`GET app/config`), falling back to the app's build-time
 * defaults. [features] holds only the keys the backend sent; resolving a key against the app's
 * `defaults.features` is the shell's job.
 */
data class AppConfig(
    val features: Map<String, Boolean>,
    val supportEmail: String?,
    val privacyPolicyUrl: String,
    val termsUrl: String,
    val defaultTheme: ThemePreference,
    /** Empty means no restriction. */
    val supportedLanguages: List<String>,
)

interface AppConfigRepository {
    /** The cached remote config, or the defaults until one has been fetched. */
    val config: StateFlow<AppConfig>

    /** Fetches and caches the config. On failure the cached config stays. */
    suspend fun refresh(): EmptyResult<DataError>
}
