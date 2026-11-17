package com.whitelabel.core.ads

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import kotlin.test.Test

/** The platform-neutral startup checks both shells run (Android's tests cover its wording too). */
class AdsWiringTest {

    private val unit = "ca-app-pub-6543210987654321/0987654321"

    private fun wiring(
        usesAds: Boolean = true,
        placements: List<AdPlacement> = listOf(AdPlacement("home", null)),
        declared: Boolean = true,
        mode: String = "on",
        units: String = "home=$unit",
        sdkLinked: Boolean = true,
        debugBuild: Boolean = false,
        nonPersonalized: Boolean = false,
    ) = adsWiring(usesAds, placements, declared, mode, units, sdkLinked, "no sdk", debugBuild, AdsPlatform.IOS, nonPersonalized)

    @Test fun `the declaration and the setting must agree in every build type`() {
        listOf(true, false).forEach { debugBuild ->
            assertFailure { wiring(declared = false, mode = "off", debugBuild = debugBuild) }
                .isInstanceOf<IllegalStateException>()
                .hasMessage("ads { } needs ads.enabled=true or debug in app.properties")
            assertFailure { wiring(usesAds = false, placements = emptyList(), mode = "off", debugBuild = debugBuild) }
                .isInstanceOf<IllegalStateException>()
                .hasMessage("ads.enabled is set but the app definition doesn't call ads { }")
        }
    }

    @Test fun `off in this build or no ads at all is off without needing the sdk`() {
        assertThat(wiring(mode = "off", units = "", sdkLinked = false)).isEqualTo(AdsWiring.OFF)
        assertThat(wiring(usesAds = false, placements = emptyList(), declared = false, mode = "off", sdkLinked = false)).isEqualTo(AdsWiring.OFF)
    }

    @Test fun `on without the sdk fails with the platform's message`() {
        assertFailure { wiring(sdkLinked = false) }.isInstanceOf<IllegalStateException>().hasMessage("no sdk")
    }

    @Test fun `ios units use the ios keys in their messages`() {
        assertFailure { wiring(units = "home=$unit;other=$unit") }
            .isInstanceOf<IllegalStateException>()
            .hasMessage("ads.ios.banner.other has no matching ads { placement(\"other\") }")
        assertFailure { wiring(units = "") }
            .isInstanceOf<IllegalStateException>()
            .hasMessage("placement 'home' has no ads.ios.banner.home")
    }

    @Test fun `debug falls back to the platform's test banner`() {
        assertThat(wiring(units = "", debugBuild = true))
            .isEqualTo(AdsWiring(enabled = true, units = mapOf("home" to "ca-app-pub-3940256099942544/2435281174")))
        assertThat(AdsPlatform.ANDROID.testBannerUnit).isEqualTo("ca-app-pub-3940256099942544/9214589741")
    }

    private val nativeUnit = "ca-app-pub-6543210987654321/1111111111"
    private val withNative = listOf(AdPlacement("home", null), AdPlacement("list", null, AdKind.NATIVE))

    @Test fun `a native placement takes its native unit`() {
        assertThat(wiring(placements = withNative, units = "home=$unit;native.list=$nativeUnit").units)
            .isEqualTo(mapOf("home" to unit, "list" to nativeUnit))
    }

    @Test fun `debug native placement without a unit gets the native test unit`() {
        assertThat(wiring(placements = withNative, units = "", debugBuild = true).units)
            .isEqualTo(mapOf("home" to "ca-app-pub-3940256099942544/2435281174", "list" to "ca-app-pub-3940256099942544/3986624511"))
    }

    @Test fun `release native placement without a unit fails`() {
        assertFailure { wiring(placements = withNative, units = "home=$unit") }
            .isInstanceOf<IllegalStateException>()
            .hasMessage("placement 'list' has no ads.ios.native.list")
    }

    @Test fun `a native unit without a native placement fails`() {
        assertFailure { wiring(units = "home=$unit;native.x=$nativeUnit") }
            .isInstanceOf<IllegalStateException>()
            .hasMessage("ads.ios.native.x has no matching ads { native(\"x\") }")
        // A native unit for a banner placement is a native unit without a native placement.
        assertFailure { wiring(units = "home=$unit;native.home=$nativeUnit") }
            .isInstanceOf<IllegalStateException>()
            .hasMessage("ads.ios.native.home has no matching ads { native(\"home\") }")
    }

    @Test fun `a banner unit for a native placement fails`() {
        assertFailure { wiring(placements = withNative, units = "home=$unit;list=$nativeUnit") }
            .isInstanceOf<IllegalStateException>()
            .hasMessage("ads.ios.banner.list is for a banner, but 'list' is declared native")
    }

    @Test fun `the native test units are Google's`() {
        assertThat(AdsPlatform.ANDROID.testNativeUnit).isEqualTo("ca-app-pub-3940256099942544/2247696110")
        assertThat(AdsPlatform.IOS.testNativeUnit).isEqualTo("ca-app-pub-3940256099942544/3986624511")
        assertThat(AdsPlatform.ANDROID.nativeKeyPrefix).isEqualTo("ads.android.native.")
        assertThat(AdsPlatform.IOS.nativeKeyPrefix).isEqualTo("ads.ios.native.")
        assertThat(NATIVE_UNIT_KEY_PREFIX).isEqualTo("native.")
    }

    @Test fun `the units string parses trimming and skipping blanks`() {
        assertThat(parseAdsUnits("")).isEqualTo(emptyMap())
        assertThat(parseAdsUnits(" home = $unit ;;b-2=x")).isEqualTo(mapOf("home" to unit, "b-2" to "x"))
    }

    // Where a shell may draw a placement.

    private val owners = mapOf(
        "home" to AdTabOwner("home", isGate = false),
        "settings" to AdTabOwner("settings", isGate = false),
        "auth" to AdTabOwner("auth", isGate = true),
    )

    private fun checkTabs(vararg placements: AdPlacement) =
        checkAdPlacementTabs(placements.toList(), ownerOf = { owners[it] }, routeName = { it.toString() })

    @Test fun `a placement under a plain tab or under none passes`() {
        checkTabs(AdPlacement("top", "home"), AdPlacement("own", null))
    }

    @Test fun `settings and the gate stay ad-free`() {
        assertFailure { checkTabs(AdPlacement("s", "settings")) }
            .isInstanceOf<IllegalArgumentException>()
            .hasMessage("No ads under settings: settings and sign-in screens stay ad-free")
        assertFailure { checkTabs(AdPlacement("g", "auth")) }
            .isInstanceOf<IllegalArgumentException>()
            .hasMessage("No ads under auth: settings and sign-in screens stay ad-free")
    }

    @Test fun `a route that isn't a tab or a tab with two banners is rejected`() {
        assertFailure { checkTabs(AdPlacement("x", "nowhere")) }
            .isInstanceOf<IllegalArgumentException>()
            .hasMessage("placement 'x' is under nowhere, which isn't a tab")
        assertFailure { checkTabs(AdPlacement("top", "home"), AdPlacement("bottom", "home")) }
            .isInstanceOf<IllegalArgumentException>()
            .hasMessage("placements 'top' and 'bottom' are both under home; a tab has one banner")
    }

    @Test fun `the shell draws the placement under the current tab root`() {
        val placements = listOf(AdPlacement("own", null), AdPlacement("top", "home"))
        assertThat(tabPlacementFor(placements) { it == "home" }).isEqualTo("top")
        assertThat(tabPlacementFor(placements) { it == "settings" }).isNull()
    }

    @Test fun `a native placement under a tab is refused`() {
        assertFailure { checkTabs(AdPlacement("n", "home", AdKind.NATIVE)) }
            .isInstanceOf<IllegalArgumentException>()
            .hasMessage("placement 'n' is native: the app places it in a list, so it has no underTab")
        // Refused before the tab lookup: a route that isn't a tab gets the native message too.
        assertFailure { checkTabs(AdPlacement("n", "nowhere", AdKind.NATIVE)) }
            .isInstanceOf<IllegalArgumentException>()
            .hasMessage("placement 'n' is native: the app places it in a list, so it has no underTab")
    }

    @Test fun `tabPlacementFor ignores native placements`() {
        val placements = listOf(AdPlacement("n", "home", AdKind.NATIVE), AdPlacement("top", "home"))
        assertThat(tabPlacementFor(placements) { it == "home" }).isEqualTo("top")
        assertThat(tabPlacementFor(listOf(AdPlacement("n", "home", AdKind.NATIVE))) { it == "home" }).isNull()
    }

    @Test fun `a non-personalized request carries AdMob's npa extra and a default one carries none`() {
        assertThat(AdRequestPolicy(nonPersonalized = true).extras).isEqualTo(mapOf("npa" to "1"))
        assertThat(AdRequestPolicy(nonPersonalized = false).extras).isEqualTo(emptyMap())
        assertThat(AdRequestPolicy.DEFAULT.nonPersonalized).isEqualTo(false)
    }

    @Test fun `the wiring carries the non-personalized setting and defaults to personalization by consent`() {
        assertThat(wiring(nonPersonalized = true).request).isEqualTo(AdRequestPolicy(nonPersonalized = true))
        assertThat(wiring().request).isEqualTo(AdRequestPolicy.DEFAULT)
        assertThat(AdsWiring.OFF.request).isEqualTo(AdRequestPolicy.DEFAULT)
    }
}
