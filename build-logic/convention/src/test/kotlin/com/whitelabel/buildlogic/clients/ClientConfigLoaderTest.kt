package com.whitelabel.buildlogic.clients

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.io.File
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ClientConfigLoaderTest {

    @TempDir
    lateinit var root: File

    private val validProps = mapOf(
        "client.slug" to "acme",
        "app.applicationId" to "com.whitelabel.acme",
        "app.name" to "Acme",
        "app.versionCode" to "1",
        "app.versionName" to "1.0.0",
        "app.deepLinkScheme" to "acme",
        "api.debug.baseUrl" to "http://10.0.2.2:3000/api/v1/",
        "api.release.baseUrl" to "https://api.platform.example.com/api/v1/",
        "defaults.support.email" to "support@acme.example",
        "defaults.legal.privacyPolicyUrl" to "https://acme.example/privacy",
        "defaults.legal.termsUrl" to "https://acme.example/terms",
        "defaults.features" to "catalog,profile",
    )

    private val assets = listOf(
        "res/mipmap-anydpi-v26/ic_launcher.xml",
        "res/mipmap-anydpi-v26/ic_launcher_round.xml",
        "res/drawable/brand_splash_icon.xml",
    )

    private fun writeClient(
        name: String,
        overrides: Map<String, String?> = emptyMap(),
        withAssets: Boolean = true,
        colors: List<String> = listOf("brand_splash_background", "ic_launcher_background"),
    ): File {
        val dir = File(root, name).also { it.mkdirs() }
        val props = (validProps + overrides).filterValues { it != null }
        File(dir, "client.properties")
            .writeText(props.entries.joinToString("\n") { "${it.key}=${it.value}" })
        if (withAssets) {
            assets.forEach { File(dir, it).apply { parentFile.mkdirs() }.writeText("<x/>") }
        }
        if (colors.isNotEmpty()) {
            File(dir, "res/values/brand.xml").apply { parentFile.mkdirs() }.writeText(
                "<resources>" + colors.joinToString("") { "<color name=\"$it\">#000000</color>" } + "</resources>",
            )
        }
        return dir
    }

    private fun problems(): List<String> {
        val ex = assertThrows(InvalidClientConfigException::class.java) { ClientConfigLoader.load(root) }
        return ex.problems
    }

    @Test
    fun `valid client parses every field`() {
        val dir = writeClient("clientA")

        val client = ClientConfigLoader.load(root).single()

        assertThat(client).isEqualTo(
            ClientConfig(
                flavorName = "clientA",
                slug = "acme",
                applicationId = "com.whitelabel.acme",
                appName = "Acme",
                versionCode = 1,
                versionName = "1.0.0",
                deepLinkScheme = "acme",
                apiBaseUrls = mapOf(
                    "debug" to "http://10.0.2.2:3000/api/v1/",
                    "release" to "https://api.platform.example.com/api/v1/",
                ),
                supportEmail = "support@acme.example",
                privacyPolicyUrl = "https://acme.example/privacy",
                termsUrl = "https://acme.example/terms",
                defaultFeatures = listOf("catalog", "profile"),
                clientDir = dir,
            ),
        )
        assertThat(client.resDir).isEqualTo(File(dir, "res"))
        assertThat(client.testDir).isEqualTo(File(dir, "test"))
    }

    @Test
    fun `results are sorted by flavor name`() {
        writeClient("zeta", mapOf("client.slug" to "zeta", "app.applicationId" to "com.w.zeta", "app.deepLinkScheme" to "zeta"))
        writeClient("alpha", mapOf("client.slug" to "alpha", "app.applicationId" to "com.w.alpha", "app.deepLinkScheme" to "alpha"))

        assertThat(ClientConfigLoader.load(root).map { it.flavorName }).containsExactly("alpha", "zeta")
    }

    @Test
    fun `base url without trailing slash is normalized`() {
        writeClient(
            "clientA",
            mapOf(
                "api.debug.baseUrl" to "http://10.0.2.2:3000/api/v1",
                "api.release.baseUrl" to "https://api.platform.example.com/api/v1",
            ),
        )

        val urls = ClientConfigLoader.load(root).single().apiBaseUrls

        assertThat(urls["debug"]).isEqualTo("http://10.0.2.2:3000/api/v1/")
        assertThat(urls["release"]).isEqualTo("https://api.platform.example.com/api/v1/")
    }

    @Test
    fun `release url must be https`() {
        writeClient("clientA", mapOf("api.release.baseUrl" to "http://api.platform.example.com/api/v1/"))

        val found = problems()

        assertThat(found).hasSize(1)
        assertThat(found.single()).contains("clients/clientA/client.properties: api.release.baseUrl")
    }

    @Test
    fun `release url must not be a local host`() {
        listOf("localhost", "127.0.0.1", "10.0.2.2", "LocalHost").forEach { host ->
            File(root, "clientA").deleteRecursively()
            writeClient("clientA", mapOf("api.release.baseUrl" to "https://$host/api/v1/"))

            val found = problems()

            assertThat(found).hasSize(1)
            assertThat(found.single()).contains("api.release.baseUrl")
        }
    }

    @Test
    fun `debug url may be cleartext to emulator host`() {
        writeClient("clientA", mapOf("api.debug.baseUrl" to "http://10.0.2.2:8080/"))

        assertThat(ClientConfigLoader.load(root).single().apiBaseUrls["debug"]).isEqualTo("http://10.0.2.2:8080/")
    }

    @Test
    fun `urls with unsupported scheme or no host are rejected`() {
        writeClient(
            "clientA",
            mapOf(
                "api.debug.baseUrl" to "ftp://10.0.2.2/",
                "defaults.legal.privacyPolicyUrl" to "http://acme.example/privacy",
                "defaults.legal.termsUrl" to "https:///terms",
            ),
        )

        val found = problems()

        assertThat(found).hasSize(3)
        assertThat(found.any { it.contains("api.debug.baseUrl") }).isEqualTo(true)
        assertThat(found.any { it.contains("defaults.legal.privacyPolicyUrl") }).isEqualTo(true)
        assertThat(found.any { it.contains("defaults.legal.termsUrl") }).isEqualTo(true)
    }

    @Test
    fun `all missing keys are reported at once`() {
        writeClient("clientA", mapOf("app.name" to null, "client.slug" to null))

        val found = problems()

        assertThat(found).hasSize(2)
        assertThat(found.any { it.startsWith("clients/clientA/client.properties: app.name") }).isEqualTo(true)
        assertThat(found.any { it.startsWith("clients/clientA/client.properties: client.slug") }).isEqualTo(true)
    }

    @Test
    fun `problems across clients are all reported`() {
        writeClient("clientA", mapOf("app.name" to null))
        writeClient("clientB", mapOf("app.versionName" to " "))

        assertThat(problems()).hasSize(2)
    }

    @Test
    fun `invalid slugs are rejected`() {
        listOf("Acme", "a", "-acme", "acme_1").forEach { slug ->
            File(root, "clientA").deleteRecursively()
            writeClient("clientA", mapOf("client.slug" to slug))

            val found = problems()

            assertThat(found).hasSize(1)
            assertThat(found.single()).contains("client.slug")
        }
    }

    @Test
    fun `invalid application ids are rejected`() {
        listOf("acme", "com.1acme", "com..acme", "Com.acme-x").forEach { id ->
            File(root, "clientA").deleteRecursively()
            writeClient("clientA", mapOf("app.applicationId" to id))

            assertThat(problems().single()).contains("app.applicationId")
        }
    }

    @Test
    fun `invalid directory names are rejected`() {
        val names = listOf("client-c", "ClientC", "main", "debug", "release", "testClient", "androidTestX")
        names.forEach { writeClient(it) }

        val found = problems()

        assertThat(found).hasSize(names.size)
        names.forEach { name -> assertThat(found.any { it.startsWith("clients/$name") }).isEqualTo(true) }
    }

    @Test
    fun `dot entries and stray files are ignored`() {
        writeClient("clientA")
        File(root, ".DS_Store").writeText("x")
        File(root, ".idea").mkdirs()
        File(root, "README.md").writeText("x")

        assertThat(ClientConfigLoader.load(root).map { it.flavorName }).containsExactly("clientA")
    }

    @Test
    fun `directory without client properties is reported`() {
        File(root, "clientA").mkdirs()

        assertThat(problems().single()).contains("clients/clientA")
    }

    @Test
    fun `duplicate application ids are rejected naming both clients`() {
        writeClient("clientA")
        writeClient("clientB", mapOf("client.slug" to "other", "app.deepLinkScheme" to "other"))

        val found = problems().single()

        assertThat(found).contains("app.applicationId")
        assertThat(found).contains("clientA")
        assertThat(found).contains("clientB")
    }

    @Test
    fun `duplicate deep link schemes are rejected`() {
        writeClient("clientA")
        writeClient("clientB", mapOf("client.slug" to "other", "app.applicationId" to "com.whitelabel.other"))

        val found = problems().single()

        assertThat(found).contains("app.deepLinkScheme")
        assertThat(found).contains("clientA")
        assertThat(found).contains("clientB")
    }

    @Test
    fun `version code must be an integer in range`() {
        listOf("abc", "0", "2100000001").forEach { code ->
            File(root, "clientA").deleteRecursively()
            writeClient("clientA", mapOf("app.versionCode" to code))

            assertThat(problems().single()).contains("app.versionCode")
        }
    }

    @Test
    fun `version code upper bound is accepted`() {
        writeClient("clientA", mapOf("app.versionCode" to "2100000000"))

        assertThat(ClientConfigLoader.load(root).single().versionCode).isEqualTo(2100000000)
    }

    @Test
    fun `default features are trimmed and blanks dropped`() {
        writeClient("clientA", mapOf("defaults.features" to "catalog, ,profile,"))

        assertThat(ClientConfigLoader.load(root).single().defaultFeatures).containsExactly("catalog", "profile")
    }

    @Test
    fun `absent features and support email are optional`() {
        writeClient("clientA", mapOf("defaults.features" to null, "defaults.support.email" to " "))

        val client = ClientConfigLoader.load(root).single()

        assertThat(client.defaultFeatures).isEqualTo(emptyList())
        assertThat(client.supportEmail).isNull()
    }

    @Test
    fun `missing launcher or splash assets are reported`() {
        writeClient("clientA", withAssets = false)

        val found = problems()

        assertThat(found).hasSize(3)
        assertThat(found.any { it.contains("clients/clientA") && it.contains("res/mipmap-anydpi-v26/ic_launcher.xml") }).isEqualTo(true)
        assertThat(found.any { it.contains("res/mipmap-anydpi-v26/ic_launcher_round.xml") }).isEqualTo(true)
        assertThat(found.any { it.contains("res/drawable/brand_splash_icon.xml") }).isEqualTo(true)
    }

    @Test
    fun `empty clients directory is an error`() {
        assertThat(problems().single()).contains("no clients")
    }

    @Test
    fun `exception message lists every problem`() {
        writeClient("clientA", mapOf("app.name" to null))

        val ex = assertThrows(InvalidClientConfigException::class.java) { ClientConfigLoader.load(root) }

        assertThat(ex.message!!).isEqualTo("Invalid client configuration:\n- " + ex.problems.joinToString("\n- "))
    }

    @Test
    fun `environment follows build type`() {
        assertThat(environmentFor("debug")).isEqualTo("DEVELOPMENT")
        assertThat(environmentFor("release")).isEqualTo("PRODUCTION")
        assertThrows(IllegalArgumentException::class.java) { environmentFor("staging") }
    }

    @Test
    fun `control characters in any value are rejected naming the key`() {
        writeClient("clientA", mapOf("app.name" to "Ac\u0007me", "app.versionName" to "1.0\u0000"))

        val found = problems()

        assertThat(found.any { it.contains("app.name") && it.contains("control") }).isEqualTo(true)
        assertThat(found.any { it.contains("app.versionName") && it.contains("control") }).isEqualTo(true)
    }

    @Test
    fun `each missing required color is a directory level problem`() {
        writeClient("clientA", colors = emptyList())

        val found = problems()

        assertThat(found).containsExactly(
            "clients/clientA: missing required color brand_splash_background",
            "clients/clientA: missing required color ic_launcher_background",
        )
    }

    @Test
    fun `required colors may live in any values xml file`() {
        val dir = writeClient("clientA", colors = listOf("ic_launcher_background"))
        File(dir, "res/values-night").mkdirs()
        File(dir, "res/values-night/extra.xml")
            .writeText("<resources><color name=\"brand_splash_background\">#111111</color></resources>")

        assertThat(ClientConfigLoader.load(root)).hasSize(1)
    }

    @Test
    fun `cleartext debug url must use a host the debug network security config permits`() {
        writeClient("clientA", mapOf("api.debug.baseUrl" to "http://192.168.1.5:3000/api/v1/"))

        val found = problems()

        assertThat(found).hasSize(1)
        assertThat(found.single().contains("api.debug.baseUrl")).isEqualTo(true)
        assertThat(found.single().contains("network_security_config.xml")).isEqualTo(true)
    }

    @Test
    fun `debug cleartext allowed for emulator host and localhost, https any host`() {
        listOf("http://localhost:3000/api/v1/", "http://10.0.2.2:3000/api/v1/", "https://staging.example.com/api/v1/")
            .forEach { url ->
                root.listFiles()?.forEach { it.deleteRecursively() }
                writeClient("clientA", mapOf("api.debug.baseUrl" to url))
                assertThat(ClientConfigLoader.load(root)).hasSize(1)
            }
    }
}
