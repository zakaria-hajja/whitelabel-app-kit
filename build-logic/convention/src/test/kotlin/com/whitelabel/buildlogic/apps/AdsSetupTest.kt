package com.whitelabel.buildlogic.apps

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AdsSetupTest {

    private companion object {
        const val APP_ID = "ca-app-pub-1234567890123456~1234567890"
        const val UNIT = "ca-app-pub-1234567890123456/1234567890"
        const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
        const val TEST_UNIT = "ca-app-pub-3940256099942544/9214589741"
        const val IOS_TEST_APP_ID = "ca-app-pub-3940256099942544~1458002511"
        const val IOS_TEST_UNIT = "ca-app-pub-3940256099942544/2435281174"
        const val NATIVE = "ca-app-pub-1234567890123456/5555555555"
        const val TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110"
        const val IOS_TEST_NATIVE = "ca-app-pub-3940256099942544/3986624511"
    }

    private val units = mapOf("home" to UNIT, "catalog" to UNIT)

    @Test fun `off is off everywhere and needs nothing`() {
        assertThat(adsSetup(ServiceMode.OFF, null, emptyMap(), emptyMap(), Platform.ANDROID, "apps/sample")).isEqualTo(
            AdsSetup(null, mapOf("debug" to "off", "release" to "off"), mapOf("debug" to emptyMap(), "release" to emptyMap()), emptyMap()),
        )
    }

    @Test fun `the setting is declared unless it is false, in every build type`() {
        assertThat(adsSetup(ServiceMode.OFF, APP_ID, units, emptyMap(), Platform.ANDROID, "apps/sample").declared).isEqualTo(false)
        assertThat(adsSetup(ServiceMode.ON, APP_ID, units, emptyMap(), Platform.ANDROID, "apps/sample").declared).isEqualTo(true)
        assertThat(adsSetup(ServiceMode.DEBUG, null, emptyMap(), emptyMap(), Platform.ANDROID, "apps/sample").declared).isEqualTo(true)
    }

    @Test fun `off ignores an app id and units`() {
        val setup = adsSetup(ServiceMode.OFF, APP_ID, units, emptyMap(), Platform.ANDROID, "apps/sample")

        assertThat(setup.modeFor).isEqualTo(mapOf("debug" to "off", "release" to "off"))
        assertThat(setup.unitsFor.getValue("release")).isEqualTo(emptyMap())
        assertThat(setup.admobAppIdFor).isEqualTo(emptyMap())
    }

    @Test fun `on is on in both build types with real units in release and test units in debug`() {
        val setup = adsSetup(ServiceMode.ON, APP_ID, units, emptyMap(), Platform.ANDROID, "apps/sample")

        assertThat(setup.dependencyConfiguration).isEqualTo("implementation")
        assertThat(setup.modeFor).isEqualTo(mapOf("debug" to "on", "release" to "on"))
        assertThat(setup.unitsFor.getValue("release")).isEqualTo(units)
        assertThat(setup.unitsFor.getValue("debug")).isEqualTo(mapOf("home" to TEST_UNIT, "catalog" to TEST_UNIT))
    }

    @Test fun `on keeps the real app id in both build types`() {
        assertThat(adsSetup(ServiceMode.ON, APP_ID, units, emptyMap(), Platform.ANDROID, "apps/sample").admobAppIdFor)
            .isEqualTo(mapOf("debug" to APP_ID, "release" to APP_ID))
    }

    @Test fun `on without the app id fails naming the android key`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            adsSetup(ServiceMode.ON, null, units, emptyMap(), Platform.ANDROID, "apps/sample")
        }

        assertThat(e.problems).isEqualTo(listOf("apps/sample: ads.enabled=true needs ads.android.appId"))
    }

    @Test fun `on without the app id names the ios key for ios`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            adsSetup(ServiceMode.ON, " ", units, emptyMap(), Platform.IOS, "apps/sample")
        }

        assertThat(e.problems.single()).contains("ads.enabled=true needs ads.ios.appId")
    }

    @Test fun `on without any unit fails because release needs at least one, naming the key shape`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            adsSetup(ServiceMode.ON, APP_ID, emptyMap(), emptyMap(), Platform.IOS, "apps/sample")
        }

        assertThat(e.problems.single()).contains("ads.enabled=true needs at least one ads.ios.banner.<placement> or ads.ios.native.<placement>")
    }

    @Test fun `on reports every problem at once`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            adsSetup(ServiceMode.ON, null, emptyMap(), emptyMap(), Platform.ANDROID, "apps/sample")
        }

        assertThat(e.problems.size).isEqualTo(2)
    }

    @Test fun `debug is on in debug with test units and off in release`() {
        val setup = adsSetup(ServiceMode.DEBUG, APP_ID, units, emptyMap(), Platform.ANDROID, "apps/sample")

        assertThat(setup.dependencyConfiguration).isEqualTo("debugImplementation")
        assertThat(setup.modeFor).isEqualTo(mapOf("debug" to "on", "release" to "off"))
        assertThat(setup.unitsFor.getValue("debug")).isEqualTo(mapOf("home" to TEST_UNIT, "catalog" to TEST_UNIT))
        assertThat(setup.unitsFor.getValue("release")).isEqualTo(emptyMap())
    }

    @Test fun `debug keeps a real app id so the consent message is found`() {
        assertThat(adsSetup(ServiceMode.DEBUG, APP_ID, units, emptyMap(), Platform.ANDROID, "apps/sample").admobAppIdFor)
            .isEqualTo(mapOf("debug" to APP_ID))
    }

    @Test fun `debug without an app id uses the test app id, per platform`() {
        assertThat(adsSetup(ServiceMode.DEBUG, null, units, emptyMap(), Platform.ANDROID, "apps/sample").admobAppIdFor)
            .isEqualTo(mapOf("debug" to TEST_APP_ID))
        assertThat(adsSetup(ServiceMode.DEBUG, null, units, emptyMap(), Platform.IOS, "apps/sample").admobAppIdFor)
            .isEqualTo(mapOf("debug" to IOS_TEST_APP_ID, "release" to IOS_TEST_APP_ID))
    }

    @Test fun `debug mode on ios keeps a real app id in release too, android release has none`() {
        assertThat(adsSetup(ServiceMode.DEBUG, APP_ID, units, emptyMap(), Platform.IOS, "apps/sample").admobAppIdFor)
            .isEqualTo(mapOf("debug" to APP_ID, "release" to APP_ID))
        assertThat(adsSetup(ServiceMode.DEBUG, null, units, emptyMap(), Platform.ANDROID, "apps/sample").admobAppIdFor.keys)
            .isEqualTo(setOf("debug"))
    }

    @Test fun `debug with no units at all passes`() {
        val setup = adsSetup(ServiceMode.DEBUG, null, emptyMap(), emptyMap(), Platform.ANDROID, "apps/sample")

        assertThat(setup.modeFor).isEqualTo(mapOf("debug" to "on", "release" to "off"))
        assertThat(setup.unitsFor.getValue("debug")).isEqualTo(emptyMap())
    }

    @Test fun `ios debug units are the ios test unit`() {
        assertThat(adsSetup(ServiceMode.DEBUG, null, mapOf("home" to UNIT), emptyMap(), Platform.IOS, "apps/sample").unitsFor.getValue("debug"))
            .isEqualTo(mapOf("home" to IOS_TEST_UNIT))
    }

    @Test fun `the units string is sorted placement=unit pairs joined by semicolons`() {
        assertThat(adsUnitsString(units)).isEqualTo("catalog=$UNIT;home=$UNIT")
        assertThat(adsUnitsString(emptyMap())).isEqualTo("")
    }

    @Test fun `the resolved app id is the ads one when ads are on, else the plain app id, else empty`() {
        val on = adsSetup(ServiceMode.DEBUG, null, units, emptyMap(), Platform.ANDROID, "apps/sample")
        val off = adsSetup(ServiceMode.OFF, null, emptyMap(), emptyMap(), Platform.ANDROID, "apps/sample")

        assertThat(resolvedAdmobAppId(on, null, "debug")).isEqualTo(TEST_APP_ID)
        assertThat(resolvedAdmobAppId(on, null, "release")).isEqualTo("")
        assertThat(resolvedAdmobAppId(on, APP_ID, "release")).isEqualTo(APP_ID)
        assertThat(resolvedAdmobAppId(off, APP_ID, "debug")).isEqualTo(APP_ID)
        assertThat(resolvedAdmobAppId(off, null, "debug")).isEqualTo("")
    }

    @Test fun `android res values publish the mode, declaration, units and non-personalized setting per build type`() {
        val debugMode = adsSetup(ServiceMode.DEBUG, null, units, emptyMap(), Platform.ANDROID, "apps/sample")
        assertThat(androidAdsResValues(debugMode, nonPersonalized = true, buildType = "debug")).isEqualTo(
            mapOf(
                "foundation_ads" to "on",
                "foundation_ads_declared" to "true",
                "foundation_ads_units" to "catalog=$TEST_UNIT;home=$TEST_UNIT",
                "foundation_ads_non_personalized" to "true",
            ),
        )
        // Ads off for the build type: nothing to request, so the setting is published as false.
        assertThat(androidAdsResValues(debugMode, nonPersonalized = true, buildType = "release").getValue("foundation_ads_non_personalized"))
            .isEqualTo("false")
        val on = adsSetup(ServiceMode.ON, APP_ID, units, emptyMap(), Platform.ANDROID, "apps/sample")
        assertThat(androidAdsResValues(on, nonPersonalized = false, buildType = "release").getValue("foundation_ads_non_personalized"))
            .isEqualTo("false")
    }

    // Native units: merged into the one units string under native.<placement>.

    @Test fun `native units are merged under native keys`() {
        val setup = adsSetup(ServiceMode.ON, APP_ID, mapOf("home" to UNIT), mapOf("list" to NATIVE), Platform.ANDROID, "apps/sample")

        assertThat(setup.unitsFor.getValue("release")).isEqualTo(mapOf("home" to UNIT, "native.list" to NATIVE))
        assertThat(adsUnitsString(setup.unitsFor.getValue("release"))).isEqualTo("home=$UNIT;native.list=$NATIVE")
        assertThat(NATIVE_UNIT_KEY_PREFIX).isEqualTo("native.")
    }

    @Test fun `debug native units are the native test unit`() {
        listOf(Platform.ANDROID to TEST_NATIVE, Platform.IOS to IOS_TEST_NATIVE).forEach { (platform, testNative) ->
            listOf(ServiceMode.ON, ServiceMode.DEBUG).forEach { mode ->
                assertThat(adsSetup(mode, APP_ID, mapOf("home" to UNIT), mapOf("list" to NATIVE), platform, "apps/sample").unitsFor.getValue("debug"))
                    .isEqualTo(mapOf("home" to platform.testBannerUnit, "native.list" to testNative))
            }
        }
        assertThat(Platform.ANDROID.testNativeUnit).isEqualTo(TEST_NATIVE)
        assertThat(Platform.IOS.testNativeUnit).isEqualTo(IOS_TEST_NATIVE)
        assertThat(Platform.ANDROID.nativeUnitKeyPrefix).isEqualTo("ads.android.native.")
        assertThat(Platform.IOS.nativeUnitKeyPrefix).isEqualTo("ads.ios.native.")
    }

    @Test fun `on with only a native unit is accepted`() {
        val setup = adsSetup(ServiceMode.ON, APP_ID, emptyMap(), mapOf("list" to NATIVE), Platform.IOS, "apps/sample")

        assertThat(setup.unitsFor.getValue("release")).isEqualTo(mapOf("native.list" to NATIVE))
    }

    @Test fun `the same name as banner and native fails naming both keys`() {
        listOf(ServiceMode.ON, ServiceMode.DEBUG).forEach { mode ->
            val e = assertThrows(InvalidAppConfigException::class.java) {
                adsSetup(mode, APP_ID, mapOf("list" to UNIT), mapOf("list" to NATIVE), Platform.ANDROID, "apps/sample")
            }

            assertThat(e.problems.single()).contains("apps/sample: ads.android.banner.list and ads.android.native.list")
        }
    }

    @Test fun `off ignores native units`() {
        val setup = adsSetup(ServiceMode.OFF, APP_ID, mapOf("list" to UNIT), mapOf("list" to NATIVE), Platform.ANDROID, "apps/sample")

        assertThat(setup.unitsFor.getValue("release")).isEqualTo(emptyMap())
    }
}
