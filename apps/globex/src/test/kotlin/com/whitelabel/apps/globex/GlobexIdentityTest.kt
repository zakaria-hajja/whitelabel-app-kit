package com.whitelabel.apps.globex

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/** Globex's exact build-time identity, as the application plugin writes it to BuildConfig. */
class GlobexIdentityTest {

    @Test fun `identity is the same in every build type`() {
        assertThat(BuildConfig.TENANT).isEqualTo("globex")
        assertThat(BuildConfig.APP_NAME).isEqualTo("Globex")
        assertThat(BuildConfig.DEEP_LINK_SCHEME).isEqualTo("globex")
        assertThat(BuildConfig.SUPPORT_EMAIL).isEqualTo("support@globex.example")
        assertThat(BuildConfig.PRIVACY_POLICY_URL).isEqualTo("https://globex.example/privacy")
        assertThat(BuildConfig.TERMS_URL).isEqualTo("https://globex.example/terms")
        assertThat(BuildConfig.DEFAULT_FEATURES).isEqualTo("")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(1)
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("1.0.0")
    }

    @Test fun `build type selects application id, api and environment`() {
        if (BuildConfig.DEBUG) {
            assertThat(BuildConfig.APPLICATION_ID).isEqualTo("com.whitelabel.globex.debug")
            assertThat(BuildConfig.API_BASE_URL).isEqualTo("http://10.0.2.2:3000/api/v1/")
            assertThat(BuildConfig.ENVIRONMENT).isEqualTo("DEVELOPMENT")
        } else {
            assertThat(BuildConfig.APPLICATION_ID).isEqualTo("com.whitelabel.globex")
            assertThat(BuildConfig.API_BASE_URL).isEqualTo("https://api.globex.example/api/v1/")
            assertThat(BuildConfig.ENVIRONMENT).isEqualTo("PRODUCTION")
        }
    }
}
