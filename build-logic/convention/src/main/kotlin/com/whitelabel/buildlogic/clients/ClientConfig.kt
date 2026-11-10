package com.whitelabel.buildlogic.clients

import java.io.File

data class ClientConfig(
    val flavorName: String,
    val slug: String,
    val applicationId: String,
    val appName: String,
    val versionCode: Int,
    val versionName: String,
    val deepLinkScheme: String,
    /** "debug" and "release" to a URL ending in "/". */
    val apiBaseUrls: Map<String, String>,
    val supportEmail: String?,
    val privacyPolicyUrl: String,
    val termsUrl: String,
    val defaultFeatures: List<String>,
    val clientDir: File,
) {
    val resDir: File get() = clientDir.resolve("res")
    val testDir: File get() = clientDir.resolve("test")
}

fun environmentFor(buildType: String): String = when (buildType) {
    "debug" -> "DEVELOPMENT"
    "release" -> "PRODUCTION"
    else -> throw IllegalArgumentException("Unknown build type '$buildType'; expected debug or release")
}
