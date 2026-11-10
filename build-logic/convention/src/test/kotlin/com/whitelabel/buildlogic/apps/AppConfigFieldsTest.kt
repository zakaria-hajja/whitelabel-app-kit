package com.whitelabel.buildlogic.apps

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.File
import org.junit.jupiter.api.Test

class AppConfigFieldsTest {

    private val config = AppConfig(
        tenant = "acme",
        applicationId = "com.whitelabel.sample",
        appName = "Sample",
        versionCode = 3,
        versionName = "1.2.0",
        deepLinkScheme = "sample",
        apiBaseUrls = mapOf("debug" to "http://10.0.2.2:3000/api/v1/", "release" to "https://api.sample.example/api/v1/"),
        supportEmail = "support@sample.example",
        privacyPolicyUrl = "https://sample.example/privacy",
        termsUrl = "https://sample.example/terms",
        defaultFeatures = listOf("catalog", "profile"),
        signing = null,
        appDir = File("sample"),
    )

    @Test
    fun `fields cover exactly the documented keys`() {
        assertThat(appConfigFields(config, "debug").keys).isEqualTo(
            setOf(
                "TENANT", "APP_NAME", "API_BASE_URL", "ENVIRONMENT", "DEEP_LINK_SCHEME",
                "SUPPORT_EMAIL", "PRIVACY_POLICY_URL", "TERMS_URL", "DEFAULT_FEATURES",
            ),
        )
    }

    @Test
    fun `values are copied from the config`() {
        val fields = appConfigFields(config, "debug")

        assertThat(fields["TENANT"]).isEqualTo("acme")
        assertThat(fields["APP_NAME"]).isEqualTo("Sample")
        assertThat(fields["DEEP_LINK_SCHEME"]).isEqualTo("sample")
        assertThat(fields["SUPPORT_EMAIL"]).isEqualTo("support@sample.example")
        assertThat(fields["PRIVACY_POLICY_URL"]).isEqualTo("https://sample.example/privacy")
        assertThat(fields["TERMS_URL"]).isEqualTo("https://sample.example/terms")
        assertThat(fields["DEFAULT_FEATURES"]).isEqualTo("catalog,profile")
    }

    @Test
    fun `absent tenant and support email become empty strings`() {
        val fields = appConfigFields(config.copy(tenant = null, supportEmail = null), "release")

        assertThat(fields["TENANT"]).isEqualTo("")
        assertThat(fields["SUPPORT_EMAIL"]).isEqualTo("")
    }

    @Test
    fun `api url and environment follow the build type`() {
        assertThat(appConfigFields(config, "debug")["API_BASE_URL"]).isEqualTo("http://10.0.2.2:3000/api/v1/")
        assertThat(appConfigFields(config, "debug")["ENVIRONMENT"]).isEqualTo("DEVELOPMENT")
        assertThat(appConfigFields(config, "release")["API_BASE_URL"]).isEqualTo("https://api.sample.example/api/v1/")
        assertThat(appConfigFields(config, "release")["ENVIRONMENT"]).isEqualTo("PRODUCTION")
    }

    @Test
    fun `resource names are prefixed and lowercased`() {
        assertThat(resourceNameFor("API_BASE_URL")).isEqualTo("foundation_api_base_url")
    }
}
