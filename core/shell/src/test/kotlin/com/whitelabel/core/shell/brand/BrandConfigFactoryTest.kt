package com.whitelabel.core.shell.brand

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.messageContains
import com.whitelabel.core.shell.R
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.branding.hasBackend
import org.junit.jupiter.api.Test

class BrandConfigFactoryTest {

    private fun brand(
        tenant: String = "tenant",
        environment: String = "DEVELOPMENT",
        supportEmail: String = "help@tenant.test",
        defaultFeatures: String = "",
        apiBaseUrl: String = "https://api.tenant.test/api/v1/",
    ): BrandConfig = brandConfigOf(
        tenant = tenant,
        appName = "Tenant",
        applicationId = "com.example.tenant",
        versionName = "1.2.3",
        environment = environment,
        apiBaseUrl = apiBaseUrl,
        deepLinkScheme = "tenant",
        supportEmail = supportEmail,
        privacyPolicyUrl = "https://tenant.test/privacy",
        termsUrl = "https://tenant.test/terms",
        defaultFeatures = defaultFeatures,
    )

    @Test fun `maps environment names`() {
        assertThat(brand(environment = "DEVELOPMENT").environment).isEqualTo(Environment.DEVELOPMENT)
        assertThat(brand(environment = "PRODUCTION").environment).isEqualTo(Environment.PRODUCTION)
    }

    @Test fun `unknown environment fails fast`() {
        assertFailure { brand(environment = "STAGING") }.isInstanceOf(IllegalStateException::class)
    }

    @Test fun `features csv becomes a set`() {
        assertThat(brand(defaultFeatures = "catalog,profile").runtimeDefaults.enabledFeatures)
            .isEqualTo(setOf("catalog", "profile"))
        assertThat(brand(defaultFeatures = "").runtimeDefaults.enabledFeatures).isEmpty()
    }

    @Test fun `blank tenant becomes null`() {
        assertThat(brand(tenant = "").tenant).isNull()
        assertThat(brand(tenant = "acme").tenant).isEqualTo("acme")
    }

    @Test fun `blank support email becomes null`() {
        assertThat(brand(supportEmail = "").runtimeDefaults.supportEmail).isNull()
        assertThat(brand(supportEmail = "help@tenant.test").runtimeDefaults.supportEmail)
            .isEqualTo("help@tenant.test")
    }

    @Test fun `identity fields pass through unchanged`() {
        val brand = brand()
        assertThat(brand.tenant).isEqualTo("tenant")
        assertThat(brand.appName).isEqualTo("Tenant")
        assertThat(brand.applicationId).isEqualTo("com.example.tenant")
        assertThat(brand.versionName).isEqualTo("1.2.3")
        assertThat(brand.apiBaseUrl).isEqualTo("https://api.tenant.test/api/v1/")
        assertThat(brand.deepLinkScheme).isEqualTo("tenant")
        assertThat(brand.runtimeDefaults.privacyPolicyUrl).isEqualTo("https://tenant.test/privacy")
        assertThat(brand.runtimeDefaults.termsUrl).isEqualTo("https://tenant.test/terms")
    }

    @Test fun `blank app name fails naming app properties via requireConfigured`() {
        assertFailure { requireConfigured("") }
            .isInstanceOf(IllegalStateException::class)
            .messageContains("app.properties")
        assertThat(requireConfigured("https://api.test/api/v1/")).isEqualTo("https://api.test/api/v1/")
    }

    @Test fun `generated string resources map to the brand`() {
        val values = mapOf(
            R.string.foundation_tenant to "",
            R.string.foundation_app_name to "Sample",
            R.string.foundation_api_base_url to "http://10.0.2.2:3000/api/v1/",
            R.string.foundation_environment to "DEVELOPMENT",
            R.string.foundation_deep_link_scheme to "sample",
            R.string.foundation_support_email to "",
            R.string.foundation_privacy_policy_url to "https://sample.test/privacy",
            R.string.foundation_terms_url to "https://sample.test/terms",
            R.string.foundation_default_features to "catalog, profile",
        )

        val brand = brandConfigFrom(strings = { values.getValue(it) }, applicationId = "com.example.sample.debug", versionName = "1.0.0")

        assertThat(brand.tenant).isNull()
        assertThat(brand.appName).isEqualTo("Sample")
        assertThat(brand.applicationId).isEqualTo("com.example.sample.debug")
        assertThat(brand.environment).isEqualTo(Environment.DEVELOPMENT)
        assertThat(brand.runtimeDefaults.enabledFeatures).isEqualTo(setOf("catalog", "profile"))
        assertThat(brand.runtimeDefaults.supportEmail).isNull()
    }

    @Test fun `blank api url means no backend`() {
        val brand = brand(apiBaseUrl = "")
        assertThat(brand.apiBaseUrl).isNull()
        assertThat(brand.hasBackend).isEqualTo(false)
        assertThat(brand(apiBaseUrl = "https://api.test/api/v1/").hasBackend).isEqualTo(true)
    }

    @Test fun `blank app name fails naming app properties`() {
        val strings = mapOf(
            R.string.foundation_tenant to "", R.string.foundation_app_name to "",
            R.string.foundation_environment to "DEVELOPMENT", R.string.foundation_api_base_url to "",
            R.string.foundation_deep_link_scheme to "x", R.string.foundation_support_email to "",
            R.string.foundation_privacy_policy_url to "https://p", R.string.foundation_terms_url to "https://t",
            R.string.foundation_default_features to "",
        )
        assertFailure { brandConfigFrom(strings::getValue, "com.example", "1.0") }
            .isInstanceOf(IllegalStateException::class)
            .messageContains("No app configuration found")
    }
}
