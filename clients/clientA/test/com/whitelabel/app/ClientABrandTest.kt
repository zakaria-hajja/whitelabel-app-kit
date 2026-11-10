package com.whitelabel.app

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.app.brand.buildConfigBrand
import com.whitelabel.core.branding.Environment
import org.junit.jupiter.api.Test

/** clientA's expectations, compiled only into the clientA variants (test source set testClientA). */
class ClientABrandTest {
    @Test fun `clientA build carries its own identity and api`() {
        val brand = buildConfigBrand()
        assertThat(brand.clientSlug).isEqualTo("acme")
        if (BuildConfig.DEBUG) {
            assertThat(brand.applicationId).isEqualTo("com.whitelabel.acme.debug")
            assertThat(brand.apiBaseUrl).isEqualTo("http://10.0.2.2:3000/api/v1/")
            assertThat(brand.environment).isEqualTo(Environment.DEVELOPMENT)
        } else {
            assertThat(brand.applicationId).isEqualTo("com.whitelabel.acme")
            assertThat(brand.apiBaseUrl).isEqualTo("https://api.platform.example.com/api/v1/")
            assertThat(brand.environment).isEqualTo(Environment.PRODUCTION)
        }
    }
}
