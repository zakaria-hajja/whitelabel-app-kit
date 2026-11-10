package com.whitelabel.buildlogic.apps

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

class AppConfigLoaderTest {

    @TempDir
    lateinit var root: File

    private val appDir: File get() = File(root, "sample")

    private val validProps = mapOf(
        "api.tenant" to "acme",
        "app.applicationId" to "com.whitelabel.sample",
        "app.name" to "Sample",
        "app.versionCode" to "1",
        "app.versionName" to "1.0.0",
        "app.deepLinkScheme" to "sample",
        "api.debug.baseUrl" to "http://10.0.2.2:3000/api/v1/",
        "api.release.baseUrl" to "https://api.sample.example/api/v1/",
        "defaults.support.email" to "support@sample.example",
        "defaults.legal.privacyPolicyUrl" to "https://sample.example/privacy",
        "defaults.legal.termsUrl" to "https://sample.example/terms",
        "defaults.features" to "catalog,profile",
    )

    private val assets = listOf(
        "src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
        "src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
        "src/main/res/drawable/brand_splash_icon.xml",
    )

    private fun writeApp(
        overrides: Map<String, String?> = emptyMap(),
        withAssets: Boolean = true,
        colors: List<String> = listOf("brand_splash_background", "ic_launcher_background"),
    ): File {
        val dir = appDir.apply { deleteRecursively(); mkdirs() }
        val props = (validProps + overrides).filterValues { it != null }
        File(dir, "app.properties").writeText(props.entries.joinToString("\n") { "${it.key}=${it.value}" })
        if (withAssets) assets.forEach { File(dir, it).apply { parentFile.mkdirs() }.writeText("<x/>") }
        if (colors.isNotEmpty()) {
            File(dir, "src/main/res/values/brand.xml").apply { parentFile.mkdirs() }.writeText(
                "<resources>" + colors.joinToString("") { "<color name=\"$it\">#000000</color>" } + "</resources>",
            )
        }
        return dir
    }

    private fun problems(): List<String> =
        assertThrows(InvalidAppConfigException::class.java) { AppConfigLoader.load(appDir) }.problems

    @Test
    fun `valid app parses every field`() {
        val dir = writeApp()

        val config = AppConfigLoader.load(dir)

        assertThat(config).isEqualTo(
            AppConfig(
                tenant = "acme",
                applicationId = "com.whitelabel.sample",
                appName = "Sample",
                versionCode = 1,
                versionName = "1.0.0",
                deepLinkScheme = "sample",
                apiBaseUrls = mapOf(
                    "debug" to "http://10.0.2.2:3000/api/v1/",
                    "release" to "https://api.sample.example/api/v1/",
                ),
                supportEmail = "support@sample.example",
                privacyPolicyUrl = "https://sample.example/privacy",
                termsUrl = "https://sample.example/terms",
                defaultFeatures = listOf("catalog", "profile"),
                signing = null,
                appDir = dir,
            ),
        )
        assertThat(config.resDir).isEqualTo(File(dir, "src/main/res"))
    }

    @Test
    fun `tenant is optional`() {
        writeApp(mapOf("api.tenant" to null))

        assertThat(AppConfigLoader.load(appDir).tenant).isNull()
    }

    @Test
    fun `tenant must match backend slug pattern when present`() {
        listOf("Acme", "a", "-acme", "acme_1").forEach { tenant ->
            writeApp(mapOf("api.tenant" to tenant))

            val found = problems()

            assertThat(found).hasSize(1)
            assertThat(found.single()).contains("apps/sample/app.properties: api.tenant")
        }
    }

    @Test
    fun `missing app properties is one problem`() {
        appDir.mkdirs()

        assertThat(problems()).containsExactly("apps/sample: missing app.properties")
    }

    @Test
    fun `base url without trailing slash is normalized`() {
        writeApp(
            mapOf(
                "api.debug.baseUrl" to "http://10.0.2.2:3000/api/v1",
                "api.release.baseUrl" to "https://api.sample.example/api/v1",
            ),
        )

        val urls = AppConfigLoader.load(appDir).apiBaseUrls

        assertThat(urls["debug"]).isEqualTo("http://10.0.2.2:3000/api/v1/")
        assertThat(urls["release"]).isEqualTo("https://api.sample.example/api/v1/")
    }

    @Test
    fun `release url must be https`() {
        writeApp(mapOf("api.release.baseUrl" to "http://api.sample.example/api/v1/"))

        assertThat(problems().single()).contains("apps/sample/app.properties: api.release.baseUrl")
    }

    @Test
    fun `release url must not be a local host`() {
        listOf("localhost", "127.0.0.1", "10.0.2.2", "LocalHost").forEach { host ->
            writeApp(mapOf("api.release.baseUrl" to "https://$host/api/v1/"))

            assertThat(problems().single()).contains("api.release.baseUrl")
        }
    }

    @Test
    fun `urls with unsupported scheme or no host are rejected`() {
        writeApp(
            mapOf(
                "api.debug.baseUrl" to "ftp://10.0.2.2/",
                "defaults.legal.privacyPolicyUrl" to "http://sample.example/privacy",
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
    fun `cleartext debug url must use a host the debug network security config permits`() {
        writeApp(mapOf("api.debug.baseUrl" to "http://192.168.1.5:3000/api/v1/"))

        val found = problems().single()

        assertThat(found).contains("api.debug.baseUrl")
        assertThat(found).contains("network_security_config.xml")
    }

    @Test
    fun `debug cleartext allowed for emulator host and localhost, https any host`() {
        listOf("http://localhost:3000/api/v1/", "http://10.0.2.2:3000/api/v1/", "https://staging.example.com/api/v1/")
            .forEach { url ->
                writeApp(mapOf("api.debug.baseUrl" to url))
                assertThat(AppConfigLoader.load(appDir).apiBaseUrls["debug"]).isEqualTo(url)
            }
    }

    @Test
    fun `all missing keys are reported at once`() {
        writeApp(mapOf("app.name" to null, "app.versionName" to " "))

        val found = problems()

        assertThat(found).hasSize(2)
        assertThat(found.any { it.startsWith("apps/sample/app.properties: app.name") }).isEqualTo(true)
        assertThat(found.any { it.startsWith("apps/sample/app.properties: app.versionName") }).isEqualTo(true)
    }

    @Test
    fun `invalid application ids are rejected`() {
        listOf("sample", "com.1sample", "com..sample", "Com.sample-x").forEach { id ->
            writeApp(mapOf("app.applicationId" to id))

            assertThat(problems().single()).contains("app.applicationId")
        }
    }

    @Test
    fun `invalid deep link scheme is rejected`() {
        writeApp(mapOf("app.deepLinkScheme" to "Sample"))

        assertThat(problems().single()).contains("app.deepLinkScheme")
    }

    @Test
    fun `version code must be an integer in range`() {
        listOf("abc", "0", "2100000001").forEach { code ->
            writeApp(mapOf("app.versionCode" to code))

            assertThat(problems().single()).contains("app.versionCode")
        }
    }

    @Test
    fun `version code upper bound is accepted`() {
        writeApp(mapOf("app.versionCode" to "2100000000"))

        assertThat(AppConfigLoader.load(appDir).versionCode).isEqualTo(2100000000)
    }

    @Test
    fun `default features are trimmed and blanks dropped`() {
        writeApp(mapOf("defaults.features" to "catalog, ,profile,"))

        assertThat(AppConfigLoader.load(appDir).defaultFeatures).containsExactly("catalog", "profile")
    }

    @Test
    fun `absent features and support email are optional`() {
        writeApp(mapOf("defaults.features" to null, "defaults.support.email" to " "))

        val config = AppConfigLoader.load(appDir)

        assertThat(config.defaultFeatures).isEqualTo(emptyList())
        assertThat(config.supportEmail).isNull()
    }

    @Test
    fun `required assets and colors are checked under src main res`() {
        writeApp(withAssets = false, colors = emptyList())

        assertThat(problems()).containsExactly(
            "apps/sample: missing required asset src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
            "apps/sample: missing required asset src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
            "apps/sample: missing required asset src/main/res/drawable/brand_splash_icon.xml",
            "apps/sample: missing required color brand_splash_background",
            "apps/sample: missing required color ic_launcher_background",
        )
    }

    @Test
    fun `required colors may live in any values xml file`() {
        val dir = writeApp(colors = listOf("ic_launcher_background"))
        File(dir, "src/main/res/values-night/extra.xml").apply { parentFile.mkdirs() }
            .writeText("<resources><color name=\"brand_splash_background\">#111111</color></resources>")

        assertThat(AppConfigLoader.load(dir).appName).isEqualTo("Sample")
    }

    @Test
    fun `control characters in any value are rejected naming the key`() {
        writeApp(mapOf("app.name" to "Sam\u0007ple", "app.versionName" to "1.0\u0000"))

        val found = problems()

        assertThat(found.any { it.contains("app.name") && it.contains("control") }).isEqualTo(true)
        assertThat(found.any { it.contains("app.versionName") && it.contains("control") }).isEqualTo(true)
    }

    @Test
    fun `exception message lists every problem`() {
        writeApp(mapOf("app.name" to null))

        val ex = assertThrows(InvalidAppConfigException::class.java) { AppConfigLoader.load(appDir) }

        assertThat(ex.message!!).isEqualTo("Invalid app configuration:\n- " + ex.problems.joinToString("\n- "))
    }

    @Test
    fun `environment follows build type`() {
        assertThat(environmentFor("debug")).isEqualTo("DEVELOPMENT")
        assertThat(environmentFor("release")).isEqualTo("PRODUCTION")
        assertThrows(IllegalArgumentException::class.java) { environmentFor("staging") }
    }

    private fun writeSigning(vararg lines: String) {
        File(appDir, "signing.properties").writeText(lines.joinToString("\n"))
    }

    @Test
    fun `absent signing file gives null`() {
        writeApp()

        assertThat(AppConfigLoader.load(appDir).signing).isNull()
    }

    @Test
    fun `incomplete signing file names the missing key`() {
        writeApp()
        File(appDir, "upload.jks").writeText("x")
        writeSigning("storeFile=upload.jks", "storePassword=s", "keyPassword=k")

        assertThat(problems()).containsExactly("apps/sample/signing.properties: keyAlias is required and must not be blank")
    }

    @Test
    fun `signing store file must exist`() {
        writeApp()
        writeSigning("storeFile=missing.jks", "storePassword=s", "keyAlias=a", "keyPassword=k")

        assertThat(problems().single()).contains("apps/sample/signing.properties: storeFile")
    }

    @Test
    fun `relative store file resolves against app dir`() {
        writeApp()
        File(appDir, "keys").mkdirs()
        File(appDir, "keys/upload.jks").writeText("x")
        writeSigning("storeFile=keys/upload.jks", "storePassword=s", "keyAlias=a", "keyPassword=k")

        assertThat(AppConfigLoader.load(appDir).signing).isEqualTo(
            AppSigning(File(appDir, "keys/upload.jks"), storePassword = "s", keyAlias = "a", keyPassword = "k"),
        )
    }
}
