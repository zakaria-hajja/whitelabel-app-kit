package com.whitelabel.apps.showcase

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/** Showcase's exact build-time identity, as the application plugin writes it to BuildConfig. */
class ShowcaseIdentityTest {

    @Test fun `identity is the same in every build type`() {
        assertThat(BuildConfig.TENANT).isEqualTo("acme")
        assertThat(BuildConfig.APP_NAME).isEqualTo("Showcase")
        assertThat(BuildConfig.DEEP_LINK_SCHEME).isEqualTo("showcase")
        assertThat(BuildConfig.SUPPORT_EMAIL).isEqualTo("support@showcase.example")
        assertThat(BuildConfig.PRIVACY_POLICY_URL).isEqualTo("https://showcase.example/privacy")
        assertThat(BuildConfig.TERMS_URL).isEqualTo("https://showcase.example/terms")
        assertThat(BuildConfig.DEFAULT_FEATURES).isEqualTo("catalog,profile")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(1)
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("1.0.0")
    }

    @Test fun `build type selects application id, api and environment`() {
        if (BuildConfig.DEBUG) {
            assertThat(BuildConfig.APPLICATION_ID).isEqualTo("com.whitelabel.showcase.debug")
            assertThat(BuildConfig.API_BASE_URL).isEqualTo("http://10.0.2.2:3000/api/v1/")
            assertThat(BuildConfig.ENVIRONMENT).isEqualTo("DEVELOPMENT")
        } else {
            assertThat(BuildConfig.APPLICATION_ID).isEqualTo("com.whitelabel.showcase")
            assertThat(BuildConfig.API_BASE_URL).isEqualTo("https://api.showcase.example/api/v1/")
            assertThat(BuildConfig.ENVIRONMENT).isEqualTo("PRODUCTION")
        }
    }
}
