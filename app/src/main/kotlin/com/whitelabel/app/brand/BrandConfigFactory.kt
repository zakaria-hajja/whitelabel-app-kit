package com.whitelabel.app.brand

import com.whitelabel.app.BuildConfig
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.branding.RuntimeDefaults

/** Maps the raw `BuildConfig` strings to a typed [BrandConfig]. Throws on an unknown environment. */
fun brandConfigOf(
    tenant: String,
    appName: String,
    applicationId: String,
    versionName: String,
    environment: String,
    apiBaseUrl: String,
    deepLinkScheme: String,
    supportEmail: String,
    privacyPolicyUrl: String,
    termsUrl: String,
    defaultFeatures: String,
): BrandConfig = BrandConfig(
    tenant = tenant.ifBlank { null },
    appName = appName,
    applicationId = applicationId,
    versionName = versionName,
    environment = Environment.entries.firstOrNull { it.name == environment }
        ?: error("Unknown environment '$environment'"),
    apiBaseUrl = apiBaseUrl,
    deepLinkScheme = deepLinkScheme,
    runtimeDefaults = RuntimeDefaults(
        supportEmail = supportEmail.ifBlank { null },
        privacyPolicyUrl = privacyPolicyUrl,
        termsUrl = termsUrl,
        enabledFeatures = defaultFeatures.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
    ),
)

/** The brand of the running variant, from the fields the application plugin writes to `BuildConfig`. */
fun buildConfigBrand(): BrandConfig = brandConfigOf(
    tenant = BuildConfig.CLIENT_SLUG,
    appName = BuildConfig.APP_NAME,
    applicationId = BuildConfig.APPLICATION_ID,
    versionName = BuildConfig.VERSION_NAME,
    environment = BuildConfig.ENVIRONMENT,
    apiBaseUrl = BuildConfig.API_BASE_URL,
    deepLinkScheme = BuildConfig.DEEP_LINK_SCHEME,
    supportEmail = BuildConfig.SUPPORT_EMAIL,
    privacyPolicyUrl = BuildConfig.PRIVACY_POLICY_URL,
    termsUrl = BuildConfig.TERMS_URL,
    defaultFeatures = BuildConfig.DEFAULT_FEATURES,
)
