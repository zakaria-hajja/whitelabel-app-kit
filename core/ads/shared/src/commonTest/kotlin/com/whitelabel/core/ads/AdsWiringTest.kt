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
        placements: List<String> = listOf("home"),
        declared: Boolean = true,
        mode: String = "on",
        units: String = "home=$unit",
        sdkLinked: Boolean = true,
        debugBuild: Boolean = false,
    ) = adsWiring(usesAds, placements, declared, mode, units, sdkLinked, "no sdk", debugBuild, AdsPlatform.IOS)

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
}
