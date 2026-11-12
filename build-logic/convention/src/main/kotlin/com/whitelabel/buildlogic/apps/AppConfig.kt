package com.whitelabel.buildlogic.apps

import java.io.File

/** Build-time identity and API settings of one app, from `apps/<name>/app.properties`. */
data class AppConfig(
    /** Sent as `X-Client-Id` when the app's backend runs in shared mode; `null` for a dedicated backend. */
    val tenant: String?,
    val applicationId: String,
    val appName: String,
    val versionCode: Int,
    val versionName: String,
    val deepLinkScheme: String,
    /** "debug" and "release" to a URL ending in "/"; `null` for an app without a backend. */
    val apiBaseUrls: Map<String, String>?,
    val supportEmail: String?,
    val privacyPolicyUrl: String,
    val termsUrl: String,
    val defaultFeatures: List<String>,
    val firebase: ServiceMode = ServiceMode.OFF,
    /** The app's AdMob app ID per platform (`ads.android.appId`, `ads.ios.appId`); Google's consent SDK needs it. */
    val adsAndroidAppId: String? = null,
    val adsIosAppId: String? = null,
    val signing: AppSigning?,
    val appDir: File,
) {
    val resDir: File get() = appDir.resolve("src/main/res")
}

fun environmentFor(buildType: String): String = when (buildType) {
    "debug" -> "DEVELOPMENT"
    "release" -> "PRODUCTION"
    else -> throw IllegalArgumentException("Unknown build type '$buildType'; expected debug or release")
}
