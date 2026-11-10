package com.whitelabel.core.shell.brand

import android.content.Context
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.branding.RuntimeDefaults
import com.whitelabel.core.shell.R

/** Maps the raw generated strings to a typed [BrandConfig]. Throws on an unknown environment. */
internal fun brandConfigOf(
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

/** The shell's own defaults are blank; a blank API URL means the app's config never reached the build. */
internal fun requireConfigured(apiBaseUrl: String): String {
    check(apiBaseUrl.isNotBlank()) {
        "No app configuration found: apply the whitelabel.android.application plugin in the app module " +
            "and define its app.properties"
    }
    return apiBaseUrl
}

/** Builds the brand from the `foundation_*` strings the application plugin generates from app.properties. */
internal fun brandConfigFrom(strings: (Int) -> String, applicationId: String, versionName: String): BrandConfig =
    brandConfigOf(
        tenant = strings(R.string.foundation_tenant),
        appName = strings(R.string.foundation_app_name),
        applicationId = applicationId,
        versionName = versionName,
        environment = strings(R.string.foundation_environment),
        apiBaseUrl = requireConfigured(strings(R.string.foundation_api_base_url)),
        deepLinkScheme = strings(R.string.foundation_deep_link_scheme),
        supportEmail = strings(R.string.foundation_support_email),
        privacyPolicyUrl = strings(R.string.foundation_privacy_policy_url),
        termsUrl = strings(R.string.foundation_terms_url),
        defaultFeatures = strings(R.string.foundation_default_features),
    )

internal fun brandConfigFromResources(context: Context): BrandConfig = brandConfigFrom(
    strings = context::getString,
    applicationId = context.packageName,
    versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
)
