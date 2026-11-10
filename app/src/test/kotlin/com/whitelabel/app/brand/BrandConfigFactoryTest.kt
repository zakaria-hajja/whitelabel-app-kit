package com.whitelabel.app.brand

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import org.junit.jupiter.api.Test

class BrandConfigFactoryTest {

    private fun brand(
        tenant: String = "tenant",
        environment: String = "DEVELOPMENT",
        supportEmail: String = "help@tenant.test",
        defaultFeatures: String = "",
    ): BrandConfig = brandConfigOf(
        tenant = tenant,
        appName = "Tenant",
        applicationId = "com.example.tenant",
        versionName = "1.2.3",
        environment = environment,
        apiBaseUrl = "https://api.tenant.test/api/v1/",
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
}
