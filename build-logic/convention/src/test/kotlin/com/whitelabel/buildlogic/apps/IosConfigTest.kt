package com.whitelabel.buildlogic.apps

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import java.io.File
import org.junit.jupiter.api.Test

class IosConfigTest {

    private fun app(urls: Map<String, String>? = mapOf("debug" to "http://10.0.2.2:3000/api/v1/", "release" to "https://api.showcase.example/api/v1/")) = AppConfig(
        tenant = "acme", applicationId = "com.whitelabel.showcase", appName = "Showcase", versionCode = 3, versionName = "1.2.0",
        deepLinkScheme = "showcase", apiBaseUrls = urls, supportEmail = "support@showcase.example",
        privacyPolicyUrl = "https://showcase.example/privacy", termsUrl = "https://showcase.example/terms",
        defaultFeatures = listOf("catalog", "profile"), signing = null, appDir = File("apps/showcase"),
    )

    @Test fun `debug uses the debug url with localhost for the simulator`() {
        assertThat(iosXcconfig(app(), "debug")).contains("WL_API_BASE_URL = http:/$()/localhost:3000/api/v1/")
    }

    @Test fun `debug bundle id gets the debug suffix as on Android`() {
        assertThat(iosXcconfig(app(), "debug")).contains("WL_BUNDLE_ID = com.whitelabel.showcase.debug")
        assertThat(iosXcconfig(app(), "release")).contains("WL_BUNDLE_ID = com.whitelabel.showcase\n")
    }

    @Test fun `release uses the release url and PRODUCTION`() {
        val release = iosXcconfig(app(), "release")
        assertThat(release).contains("WL_API_BASE_URL = https:/$()/api.showcase.example/api/v1/")
        assertThat(release).contains("WL_ENVIRONMENT = PRODUCTION")
        assertThat(release).doesNotContain("localhost")
    }

    @Test fun `a backendless app has an empty url`() {
        assertThat(iosXcconfig(app(urls = null), "debug")).contains("WL_API_BASE_URL = \n")
    }

    @Test fun `every published field is written once, with the version`() {
        val debug = iosXcconfig(app(), "debug")
        listOf(
            "WL_APP_NAME = Showcase", "WL_TENANT = acme", "WL_DEEP_LINK_SCHEME = showcase", "WL_ENVIRONMENT = DEVELOPMENT",
            "WL_SUPPORT_EMAIL = support@showcase.example", "WL_PRIVACY_POLICY_URL = https:/$()/showcase.example/privacy",
            "WL_TERMS_URL = https:/$()/showcase.example/terms", "WL_DEFAULT_FEATURES = catalog,profile",
            "WL_VERSION_NAME = 1.2.0", "WL_VERSION_CODE = 3",
        ).forEach { assertThat(debug).contains(it) }
    }

    @Test fun `values with spaces or equals signs are written verbatim after the key`() {
        assertThat(iosXcconfig(app().copy(appName = "My App = Best"), "debug")).contains("WL_APP_NAME = My App = Best\n")
    }
}
