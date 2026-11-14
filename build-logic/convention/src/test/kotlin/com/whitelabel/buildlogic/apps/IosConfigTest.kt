package com.whitelabel.buildlogic.apps

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import java.io.File
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class IosConfigTest {

    private companion object {
        const val IOS_ID = "ca-app-pub-6543210987654321~0987654321"
    }

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

    @Test fun `writes WL_FIREBASE per build type`() {
        val config = app().copy(firebase = ServiceMode.DEBUG)

        assertThat(iosXcconfig(config, "debug", firebaseConfigBuildTypes = emptySet())).contains("WL_FIREBASE = fake\n")
        assertThat(iosXcconfig(config, "release", firebaseConfigBuildTypes = emptySet())).contains("WL_FIREBASE = off\n")
        assertThat(iosXcconfig(config, "debug", firebaseConfigBuildTypes = FIREBASE_BUILD_TYPES)).contains("WL_FIREBASE = fake\n")
        val withId = config.copy(adsIosAppId = IOS_ID)
        assertThat(iosXcconfig(withId, "debug", firebaseConfigBuildTypes = FIREBASE_BUILD_TYPES)).contains("WL_FIREBASE = on\n")
        assertThat(iosXcconfig(withId, "release", firebaseConfigBuildTypes = FIREBASE_BUILD_TYPES)).contains("WL_FIREBASE = off\n")
        assertThat(iosXcconfig(app(), "debug")).contains("WL_FIREBASE = off\n")
    }

    @Test fun `a missing plist with firebase on fails generation`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            iosXcconfig(app().copy(firebase = ServiceMode.ON, adsIosAppId = IOS_ID), "debug", firebaseConfigBuildTypes = emptySet())
        }

        assertThat(e.message!!).contains("apps/showcase: firebase.enabled=true needs GoogleService-Info.plist")
    }

    @Test fun `firebase on with both plists writes on in both`() {
        val config = app().copy(firebase = ServiceMode.ON, adsIosAppId = IOS_ID)

        assertThat(iosXcconfig(config, "debug", firebaseConfigBuildTypes = setOf("debug", "release"))).contains("WL_FIREBASE = on\n")
        assertThat(iosXcconfig(config, "release", firebaseConfigBuildTypes = setOf("debug", "release"))).contains("WL_FIREBASE = on\n")
    }

    @Test fun `firebase on with only the debug plist fails naming release`() {
        val config = app().copy(firebase = ServiceMode.ON, adsIosAppId = IOS_ID)

        for (buildType in listOf("debug", "release")) {
            val e = assertThrows(InvalidAppConfigException::class.java) {
                iosXcconfig(config, buildType, firebaseConfigBuildTypes = setOf("debug"))
            }

            assertThat(e.message!!).contains("apps/showcase: firebase.enabled=true needs GoogleService-Info.plist for release")
        }
    }

    @Test fun `debug mode with only the debug plist is on in debug, off in release`() {
        val config = app().copy(firebase = ServiceMode.DEBUG, adsIosAppId = IOS_ID)

        assertThat(iosXcconfig(config, "debug", firebaseConfigBuildTypes = setOf("debug"))).contains("WL_FIREBASE = on\n")
        assertThat(iosXcconfig(config, "release", firebaseConfigBuildTypes = setOf("debug"))).contains("WL_FIREBASE = off\n")
    }

    @Test fun `writes WL_ADMOB_APP_ID, empty when absent, in both build types`() {
        assertThat(iosXcconfig(app().copy(adsIosAppId = IOS_ID), "debug")).contains("WL_ADMOB_APP_ID = $IOS_ID\n")
        assertThat(iosXcconfig(app().copy(adsIosAppId = IOS_ID), "release")).contains("WL_ADMOB_APP_ID = $IOS_ID\n")
        assertThat(iosXcconfig(app(), "debug")).contains("WL_ADMOB_APP_ID = \n")
    }

    @Test fun `the android app id never reaches the ios config`() {
        val android = "ca-app-pub-1234567890123456~1234567890"

        assertThat(iosXcconfig(app().copy(adsAndroidAppId = android), "debug")).doesNotContain(android)
    }

    @Test fun `firebase on without the ios app id fails generation naming the key`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            iosXcconfig(app().copy(firebase = ServiceMode.ON), "debug", firebaseConfigBuildTypes = FIREBASE_BUILD_TYPES)
        }

        assertThat(e.message!!).contains("apps/showcase: firebase.enabled=true needs ads.ios.appId (the AdMob app ID; Google's consent SDK requires it)")
    }

    @Test fun `debug with the plist but no ios app id writes fake`() {
        val config = app().copy(firebase = ServiceMode.DEBUG)

        assertThat(iosXcconfig(config, "debug", firebaseConfigBuildTypes = FIREBASE_BUILD_TYPES)).contains("WL_FIREBASE = fake\n")
    }

    @Test fun `an app without firebase exports the consent and firebase types WhitelabelKit names, but no crashlytics`() {
        assertThat(iosFirebaseFramework(ServiceMode.OFF)).isEqualTo(
            IosFirebaseFramework(crashLinkPlugin = false, modules = listOf(":core:consent:shared", ":core:firebase:shared", ":core:ads:shared")),
        )
    }

    @Test fun `an app with firebase on or in debug also gets the crashlytics module and the link plugin`() {
        val withFirebase = IosFirebaseFramework(
            crashLinkPlugin = true,
            modules = listOf(":core:consent:shared", ":core:firebase:shared", ":core:ads:shared", ":core:firebase:crashlytics"),
        )
        assertThat(iosFirebaseFramework(ServiceMode.ON)).isEqualTo(withFirebase)
        assertThat(iosFirebaseFramework(ServiceMode.DEBUG)).isEqualTo(withFirebase)
    }

    private val unit = "ca-app-pub-6543210987654321/0987654321"

    @Test fun `ads off writes off, no units, no tracking and no message`() {
        val out = iosXcconfig(app(), "debug")

        assertThat(out).contains("WL_ADS = off\n")
        assertThat(out).contains("WL_ADS_UNITS = \n")
        assertThat(out).contains("WL_ADS_TRACKING = no\n")
        assertThat(out).contains("WL_ADS_TRACKING_MESSAGE = \n")
    }

    @Test fun `ads on writes the mode, the sorted units, tracking and its message per build type`() {
        val config = app().copy(
            ads = ServiceMode.ON, adsIosAppId = IOS_ID, adsIosUnits = mapOf("home" to unit, "catalog" to unit),
            iosRequestTracking = true, iosTrackingMessage = "Ads stay relevant.",
        )

        val release = iosXcconfig(config, "release")
        assertThat(release).contains("WL_ADS = on\n")
        assertThat(release).contains("WL_ADS_UNITS = catalog=$unit;home=$unit\n")
        assertThat(release).contains("WL_ADS_TRACKING = yes\n")
        assertThat(release).contains("WL_ADS_TRACKING_MESSAGE = Ads stay relevant.\n")
        // Debug swaps the units for Google's test unit.
        assertThat(iosXcconfig(config, "debug")).contains("WL_ADS_UNITS = catalog=ca-app-pub-3940256099942544/2435281174;home=ca-app-pub-3940256099942544/2435281174\n")
    }

    @Test fun `ads in debug mode is off in release, with no tracking and no message`() {
        val config = app().copy(ads = ServiceMode.DEBUG, iosRequestTracking = true, iosTrackingMessage = "Msg")

        val release = iosXcconfig(config, "release")
        assertThat(release).contains("WL_ADS = off\n")
        assertThat(release).contains("WL_ADS_TRACKING = no\n")
        assertThat(release).contains("WL_ADS_TRACKING_MESSAGE = \n")
        assertThat(iosXcconfig(config, "debug")).contains("WL_ADS = on\n")
        assertThat(iosXcconfig(config, "debug")).contains("WL_ADS_TRACKING = yes\n")
    }

    @Test fun `WL_ADS_DECLARED says whether ads enabled is set, the same in both build types`() {
        listOf("debug", "release").forEach { buildType ->
            assertThat(iosXcconfig(app(), buildType)).contains("WL_ADS_DECLARED = false\n")
            assertThat(iosXcconfig(app().copy(ads = ServiceMode.DEBUG), buildType)).contains("WL_ADS_DECLARED = true\n")
            val on = app().copy(ads = ServiceMode.ON, adsIosAppId = IOS_ID, adsIosUnits = mapOf("home" to unit))
            assertThat(iosXcconfig(on, buildType)).contains("WL_ADS_DECLARED = true\n")
        }
    }

    @Test fun `ads in debug mode without an app id writes the test app id in both build types`() {
        val config = app().copy(ads = ServiceMode.DEBUG)

        assertThat(iosXcconfig(config, "debug")).contains("WL_ADMOB_APP_ID = ca-app-pub-3940256099942544~1458002511\n")
        assertThat(iosXcconfig(config, "release")).contains("WL_ADMOB_APP_ID = ca-app-pub-3940256099942544~1458002511\n")
    }

    @Test fun `a real ios app id is kept in debug when ads are on`() {
        assertThat(iosXcconfig(app().copy(ads = ServiceMode.DEBUG, adsIosAppId = IOS_ID), "debug")).contains("WL_ADMOB_APP_ID = $IOS_ID\n")
    }

    @Test fun `ads true without the ios app id fails generation naming the key`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            iosXcconfig(app().copy(ads = ServiceMode.ON, adsIosUnits = mapOf("home" to unit)), "debug")
        }

        assertThat(e.message!!).contains("apps/showcase: ads.enabled=true needs ads.ios.appId")
    }

    private fun messageLine(message: String): String {
        val config = app().copy(ads = ServiceMode.ON, adsIosAppId = IOS_ID, adsIosUnits = mapOf("home" to unit), iosRequestTracking = true, iosTrackingMessage = message)
        return iosXcconfig(config, "release")
    }

    @Test fun `a tracking message keeps quotes and escapes every double slash`() {
        val out = messageLine("Say \"hi\" at https://a.example//b ///c")

        assertThat(out).contains("WL_ADS_TRACKING_MESSAGE = Say \"hi\" at https:/$()/a.example/$()/b /$()/$()/c\n")
    }

    @Test fun `a tracking message with newlines and tabs stays on one line`() {
        val out = messageLine("one\ntwo\r\nthree\tfour")

        assertThat(out).contains("WL_ADS_TRACKING_MESSAGE = one two three four\n")
        assertThat(out.lines().count { it.startsWith("WL_") }).isEqualTo(out.lines().count { it.isNotBlank() && !it.startsWith("//") })
    }
}
