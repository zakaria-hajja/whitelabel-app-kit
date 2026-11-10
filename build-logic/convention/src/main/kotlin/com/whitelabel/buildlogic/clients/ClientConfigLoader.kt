package com.whitelabel.buildlogic.clients

import java.io.File
import java.net.URI
import java.util.Properties

object ClientConfigLoader {

    private val FLAVOR_NAME = Regex("^[a-z][a-zA-Z0-9]*$")
    private val RESERVED_NAMES = setOf("main", "debug", "release")
    private val RESERVED_PREFIXES = listOf("test", "androidTest")
    private val SLUG = Regex("^[a-z0-9][a-z0-9-]{1,62}$")
    private val APPLICATION_ID = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private val DEEP_LINK_SCHEME = Regex("^[a-z][a-z0-9+.-]*$")
    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")
    private const val MAX_VERSION_CODE = 2_100_000_000

    private val REQUIRED_ASSETS = listOf(
        "res/mipmap-anydpi-v26/ic_launcher.xml",
        "res/mipmap-anydpi-v26/ic_launcher_round.xml",
        "res/drawable/brand_splash_icon.xml",
    )

    fun load(clientsDir: File): List<ClientConfig> {
        val problems = mutableListOf<String>()
        val clients = mutableListOf<ClientConfig>()

        val dirs = clientsDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            .orEmpty()
            .sortedBy { it.name }

        if (dirs.isEmpty()) problems += "clients: no clients found in ${clientsDir.path}"

        for (dir in dirs) {
            val nameProblem = flavorNameProblem(dir.name)
            if (nameProblem != null) {
                problems += "clients/${dir.name}: $nameProblem"
                continue
            }
            parseClient(dir, problems)?.let { clients += it }
        }

        problems += duplicates(clients, "app.applicationId") { it.applicationId }
        problems += duplicates(clients, "app.deepLinkScheme") { it.deepLinkScheme }

        if (problems.isNotEmpty()) throw InvalidClientConfigException(problems)
        return clients.sortedBy { it.flavorName }
    }

    private fun flavorNameProblem(name: String): String? = when {
        !FLAVOR_NAME.matches(name) -> "invalid directory name '$name', must match ${FLAVOR_NAME.pattern}"
        name in RESERVED_NAMES -> "directory name '$name' is reserved"
        RESERVED_PREFIXES.any { name.startsWith(it) } -> "directory name '$name' must not start with test or androidTest"
        else -> null
    }

    private fun duplicates(
        clients: List<ClientConfig>,
        key: String,
        value: (ClientConfig) -> String,
    ): List<String> = clients.groupBy(value).filterValues { it.size > 1 }.map { (v, group) ->
        val names = group.map { it.flavorName }.sorted()
        "clients/${names.first()}/client.properties: $key '$v' is also used by " +
            names.drop(1).joinToString(", ") { "clients/$it" }
    }

    private fun parseClient(dir: File, problems: MutableList<String>): ClientConfig? {
        val file = File(dir, "client.properties")
        if (!file.isFile) {
            problems += "clients/${dir.name}: client.properties is missing"
            return null
        }
        val props = Properties().also { p -> file.reader(Charsets.UTF_8).use { p.load(it) } }
        val before = problems.size
        val prefix = "clients/${dir.name}/client.properties"

        fun fail(key: String, message: String) {
            problems += "$prefix: $key $message"
        }

        fun value(key: String): String? =
            props.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }

        fun required(key: String): String? =
            value(key) ?: run { fail(key, "is required and must not be blank"); null }

        fun matching(key: String, regex: Regex): String? =
            required(key)?.let {
                if (regex.matches(it)) it else { fail(key, "'$it' must match ${regex.pattern}"); null }
            }

        fun url(key: String, httpsOnly: Boolean, notLocal: Boolean = false, normalize: Boolean = false): String? {
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
                normalize && !raw.endsWith("/") -> "$raw/"
                else -> raw
            }
        }

        val slug = matching("client.slug", SLUG)
        val applicationId = matching("app.applicationId", APPLICATION_ID)
        val appName = required("app.name")
        val versionCode = required("app.versionCode")?.let { raw ->
            raw.toIntOrNull()?.takeIf { it in 1..MAX_VERSION_CODE }
                ?: run { fail("app.versionCode", "'$raw' must be an integer between 1 and $MAX_VERSION_CODE"); null }
        }
        val versionName = required("app.versionName")
        val deepLinkScheme = matching("app.deepLinkScheme", DEEP_LINK_SCHEME)
        val debugUrl = url("api.debug.baseUrl", httpsOnly = false, normalize = true)
        val releaseUrl = url("api.release.baseUrl", httpsOnly = true, notLocal = true, normalize = true)
        val privacyUrl = url("defaults.legal.privacyPolicyUrl", httpsOnly = true)
        val termsUrl = url("defaults.legal.termsUrl", httpsOnly = true)
        val features = value("defaults.features").orEmpty()
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }

        REQUIRED_ASSETS.filterNot { File(dir, it).isFile }.forEach {
            problems += "clients/${dir.name}: missing required asset $it"
        }

        if (problems.size != before) return null
        return ClientConfig(
            flavorName = dir.name,
            slug = slug!!,
            applicationId = applicationId!!,
            appName = appName!!,
            versionCode = versionCode!!,
            versionName = versionName!!,
            deepLinkScheme = deepLinkScheme!!,
            apiBaseUrls = mapOf("debug" to debugUrl!!, "release" to releaseUrl!!),
            supportEmail = value("defaults.support.email"),
            privacyPolicyUrl = privacyUrl!!,
            termsUrl = termsUrl!!,
            defaultFeatures = features,
            clientDir = dir,
        )
    }
}
