package com.whitelabel.buildlogic.apps

import java.io.File
import java.net.URI
import java.util.Properties

/**
 * Loads and validates `<appDir>/app.properties`. Every problem is collected and reported in one
 * [InvalidAppConfigException], each naming the file and key. Rules: design spec section 3.
 */
object AppConfigLoader {

    private val TENANT = Regex("^[a-z0-9][a-z0-9-]{1,62}$")
    private val APPLICATION_ID = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private val ADMOB_APP_ID = Regex("^ca-app-pub-\\d{16}~\\d{10}$")
    private val DEEP_LINK_SCHEME = Regex("^[a-z][a-z0-9+.-]*$")
    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")
    // Must match core/shell/src/debug/res/xml/network_security_config.xml.
    private val DEBUG_CLEARTEXT_HOSTS = setOf("10.0.2.2", "localhost")
    private val REQUIRED_COLORS = listOf("brand_splash_background", "ic_launcher_background")
    private val COLOR_NAME = Regex("<color\\s+[^>]*name\\s*=\\s*\"([^\"]+)\"")
    private const val MAX_VERSION_CODE = 2_100_000_000

    private val REQUIRED_ASSETS = listOf(
        "src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
        "src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
        "src/main/res/drawable/brand_splash_icon.xml",
        "src/main/res/drawable/ic_launcher_foreground.xml",
    )

    /** [label] names the app in messages: its directory relative to the build's root. */
    fun load(appDir: File, label: String = "apps/${appDir.name}"): AppConfig {
        val problems = mutableListOf<String>()
        val config = parse(appDir, label, problems)
        if (problems.isNotEmpty() || config == null) throw InvalidAppConfigException(problems)
        return config
    }

    private fun parse(dir: File, label: String, problems: MutableList<String>): AppConfig? {
        val file = File(dir, "app.properties")
        if (!file.isFile) {
            problems += "$label: missing app.properties"
            return null
        }
        val props = Properties().also { p -> file.reader(Charsets.UTF_8).use { p.load(it) } }
        val prefix = "$label/app.properties"

        fun fail(key: String, message: String) {
            problems += "$prefix: $key $message"
        }

        props.stringPropertyNames().sorted().forEach { key ->
            if (props.getProperty(key).any { it.isISOControl() }) fail(key, "must not contain control characters")
        }

        fun value(key: String): String? =
            props.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }

        fun required(key: String): String? =
            value(key) ?: run { fail(key, "is required and must not be blank"); null }

        fun check(key: String, raw: String?, regex: Regex): String? =
            raw?.let { if (regex.matches(it)) it else { fail(key, "'$it' must match ${regex.pattern}"); null } }

        fun url(key: String, httpsOnly: Boolean, notLocal: Boolean = false, normalize: Boolean = false, cleartextHosts: Set<String>? = null): String? {
            val raw = required(key) ?: return null
            val uri = runCatching { URI(raw) }.getOrNull()
            val scheme = uri?.scheme?.lowercase()
            val host = uri?.host
            val allowed = if (httpsOnly) setOf("https") else setOf("http", "https")
            return when {
                uri == null || scheme == null || host.isNullOrEmpty() ->
                    fail(key, "'$raw' must be an absolute URL with a host").let { null }
                scheme !in allowed ->
                    fail(key, "'$raw' must use ${allowed.joinToString(" or ")}").let { null }
                notLocal && host.lowercase() in LOCAL_HOSTS ->
                    fail(key, "'$raw' must not point to a local host").let { null }
                scheme == "http" && cleartextHosts != null && host.lowercase() !in cleartextHosts ->
                    fail(
                        key,
                        "'$raw' uses cleartext http to host '$host', but the debug network security config " +
                            "(core/shell/src/debug/res/xml/network_security_config.xml) only permits " +
                            "${cleartextHosts.sorted().joinToString(" and ")}; use one of them or https",
                    ).let { null }
                normalize && !raw.endsWith("/") -> "$raw/"
                else -> raw
            }
        }

        val before = problems.size
        val tenant = check("api.tenant", value("api.tenant"), TENANT)
        val applicationId = check("app.applicationId", required("app.applicationId"), APPLICATION_ID)
        val appName = required("app.name")
        val versionCode = required("app.versionCode")?.let { raw ->
            raw.toIntOrNull()?.takeIf { it in 1..MAX_VERSION_CODE }
                ?: run { fail("app.versionCode", "'$raw' must be an integer between 1 and $MAX_VERSION_CODE"); null }
        }
        val versionName = required("app.versionName")
        val deepLinkScheme = check("app.deepLinkScheme", required("app.deepLinkScheme"), DEEP_LINK_SCHEME)
        // Both base URLs, or neither (an app without a backend).
        val hasDebugUrl = value("api.debug.baseUrl") != null
        val hasReleaseUrl = value("api.release.baseUrl") != null
        val hasBackend = hasDebugUrl && hasReleaseUrl
        if (hasDebugUrl != hasReleaseUrl) {
            problems += "$prefix: api.debug.baseUrl and api.release.baseUrl must be set together"
        }
        if (!hasDebugUrl && !hasReleaseUrl && value("api.tenant") != null) {
            problems += "$prefix: api.tenant needs api.debug.baseUrl and api.release.baseUrl"
        }
        val debugUrl = if (hasBackend) url("api.debug.baseUrl", httpsOnly = false, normalize = true, cleartextHosts = DEBUG_CLEARTEXT_HOSTS) else null
        val releaseUrl = if (hasBackend) url("api.release.baseUrl", httpsOnly = true, notLocal = true, normalize = true) else null
        val privacyUrl = url("defaults.legal.privacyPolicyUrl", httpsOnly = true)
        val termsUrl = url("defaults.legal.termsUrl", httpsOnly = true)
        val features = value("defaults.features").orEmpty()
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }

        REQUIRED_ASSETS.filterNot { File(dir, it).isFile }.forEach {
            problems += "$label: missing required asset $it"
        }

        val declaredColors = File(dir, "src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") }
            .orEmpty()
            .flatMap { d -> d.listFiles { f -> f.isFile && f.extension == "xml" }.orEmpty().toList() }
            .flatMap { f -> COLOR_NAME.findAll(f.readText()).map { it.groupValues[1] }.toList() }
            .toSet()
        REQUIRED_COLORS.filterNot { it in declaredColors }.forEach {
            problems += "$label: missing required color $it"
        }

        val firebase = when (val raw = value("firebase.enabled")) {
            null, "false" -> ServiceMode.OFF
            "true" -> ServiceMode.ON
            "debug" -> ServiceMode.DEBUG
            else -> { fail("firebase.enabled", "'$raw' must be false, true or debug"); null }
        }

        val adsAndroidAppId = check("ads.android.appId", value("ads.android.appId"), ADMOB_APP_ID)
        val adsIosAppId = check("ads.ios.appId", value("ads.ios.appId"), ADMOB_APP_ID)

        val signing = parseSigning(dir, label, problems)

        if (problems.size != before) return null
        return AppConfig(
            tenant = tenant,
            applicationId = applicationId!!,
            appName = appName!!,
            versionCode = versionCode!!,
            versionName = versionName!!,
            deepLinkScheme = deepLinkScheme!!,
            apiBaseUrls = if (hasBackend) mapOf("debug" to debugUrl!!, "release" to releaseUrl!!) else null,
            supportEmail = value("defaults.support.email"),
            privacyPolicyUrl = privacyUrl!!,
            termsUrl = termsUrl!!,
            defaultFeatures = features,
            firebase = firebase!!,
            adsAndroidAppId = adsAndroidAppId,
            adsIosAppId = adsIosAppId,
            signing = signing,
            appDir = dir,
        )
    }

    /** Parses the optional, gitignored `signing.properties`; absent means the release build stays unsigned. */
    private fun parseSigning(dir: File, label: String, problems: MutableList<String>): AppSigning? {
        val file = File(dir, "signing.properties")
        if (!file.isFile) return null
        val props = Properties().also { p -> file.reader(Charsets.UTF_8).use { p.load(it) } }
        val prefix = "$label/signing.properties"
        fun required(key: String): String? =
            props.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
                ?: run { problems += "$prefix: $key is required and must not be blank"; null }

        val storePath = required("storeFile")
        val storePassword = required("storePassword")
        val keyAlias = required("keyAlias")
        val keyPassword = required("keyPassword")
        val storeFile = storePath?.let { File(it).takeIf(File::isAbsolute) ?: File(dir, it) }
        if (storeFile != null && !storeFile.isFile) {
            problems += "$prefix: storeFile '$storePath' does not exist"
            return null
        }
        if (storeFile == null || storePassword == null || keyAlias == null || keyPassword == null) return null
        return AppSigning(storeFile, storePassword, keyAlias, keyPassword)
    }
}
