package com.whitelabel.buildlogic.apps

/**
 * The values the application plugin publishes for one build type. The same map feeds both the
 * app's `BuildConfig` fields and the generated `foundation_*` string resources the shell reads,
 * so the two can't drift.
 */
fun appConfigFields(config: AppConfig, buildType: String): Map<String, String> = mapOf(
    "TENANT" to config.tenant.orEmpty(),
    "APP_NAME" to config.appName,
    "API_BASE_URL" to config.apiBaseUrls.getValue(buildType),
    "ENVIRONMENT" to environmentFor(buildType),
    "DEEP_LINK_SCHEME" to config.deepLinkScheme,
    "SUPPORT_EMAIL" to config.supportEmail.orEmpty(),
    "PRIVACY_POLICY_URL" to config.privacyPolicyUrl,
    "TERMS_URL" to config.termsUrl,
    "DEFAULT_FEATURES" to config.defaultFeatures.joinToString(","),
)

fun resourceNameFor(field: String): String = "foundation_" + field.lowercase()
