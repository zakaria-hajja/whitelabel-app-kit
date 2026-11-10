package com.whitelabel.core.branding

/**
 * The client this build was made for, fixed at build time. `:app` builds it from `BuildConfig`
 * and provides it as a singleton; features inject it instead of reading `BuildConfig` and never
 * compare client names.
 */
data class BrandConfig(
    val clientSlug: String,
    val appName: String,
    val applicationId: String,
    val versionName: String,
    val environment: Environment,
    /** Versioned API root, ending in `/`. */
    val apiBaseUrl: String,
    val deepLinkScheme: String,
    val runtimeDefaults: RuntimeDefaults,
)

/** Derived from the build type: debug is [DEVELOPMENT], release is [PRODUCTION]. */
enum class Environment { DEVELOPMENT, PRODUCTION }

/** Values used until (or when) the remote app config is unavailable. */
data class RuntimeDefaults(
    val supportEmail: String?,
    val privacyPolicyUrl: String,
    val termsUrl: String,
    val enabledFeatures: Set<String>,
)
