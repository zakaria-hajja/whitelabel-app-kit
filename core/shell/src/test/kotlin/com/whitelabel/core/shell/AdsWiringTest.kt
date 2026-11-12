package com.whitelabel.core.shell

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.whitelabel.core.ads.AdPlacement
import com.whitelabel.core.ads.AdsController
import com.whitelabel.core.ads.AdsServicesContributor
import com.whitelabel.core.ads.AdsWiring
import com.whitelabel.core.ads.parseAdsUnits
import com.whitelabel.core.ads.BannerState
import com.whitelabel.core.ads.HideReason
import com.whitelabel.core.consent.Consent
import com.whitelabel.core.consent.ConsentState
import com.whitelabel.core.datastore.StorePaths
import com.whitelabel.core.network.ModuleContributor
import com.whitelabel.core.shell.brand.DEBUG_TEST_BANNER_UNIT
import com.whitelabel.core.shell.brand.adsContributorPresent
import com.whitelabel.core.shell.brand.adsWiring
import com.whitelabel.core.shell.brand.foundationModules
import okio.FileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class AdsWiringTest {

    private val adsMarked = object : AdsServicesContributor {
        override fun modules() = emptyList<Module>()
    }

    private val withHome = appDefinition {
        add(entry("a", RouteA))
        add(entry("b", RouteB))
        ads { placement("home", underTab = RouteA) }
    }

    // The DSL, checked when the definition is built.

    @Test fun `ads() records the placements and marks the app as using ads`() {
        assertThat(withHome.usesAds).isEqualTo(true)
        assertThat(withHome.adPlacements).containsExactly(AdPlacement("home", RouteA))
        assertThat(appDefinition { add(entry("a", RouteA)) }.usesAds).isEqualTo(false)
    }

    @Test fun `ads() may be called more than once, and a name repeated across calls is still rejected`() {
        val definition = appDefinition {
            add(entry("a", RouteA))
            ads { placement("home") }
            ads { placement("detail") }
        }
        assertThat(definition.adPlacements.map { it.name }).containsExactly("home", "detail")
        assertFailure {
            appDefinition {
                add(entry("a", RouteA))
                ads { placement("home") }
                ads { placement("home") }
            }
        }.isInstanceOf<IllegalArgumentException>().hasMessage("placement 'home' is declared twice")
    }

    @Test fun `shell refuses a placement under settings`() {
        assertFailure {
            appDefinition {
                add(entry("a", RouteA))
                add(entry("settings", RouteB))
                ads { placement("bottom", underTab = RouteB) }
            }
        }.isInstanceOf<IllegalArgumentException>()
            .hasMessage("No ads under settings: settings and sign-in screens stay ad-free")
    }

    @Test fun `shell refuses a placement under the gate`() {
        assertFailure {
            appDefinition {
                add(entry("auth", gate = Gate(RouteC)))
                add(entry("a", RouteA))
                ads { placement("login", underTab = RouteC) }
            }
        }.isInstanceOf<IllegalArgumentException>()
            .hasMessage("No ads under auth: settings and sign-in screens stay ad-free")
    }

    @Test fun `a placement under a route that isn't a tab is rejected`() {
        assertFailure {
            appDefinition {
                add(entry("a", RouteA))
                ads { placement("home", underTab = RouteC) }
            }
        }.isInstanceOf<IllegalArgumentException>()
            .hasMessage("placement 'home' is under RouteC, which isn't a tab")
    }

    @Test fun `two placements under one tab are rejected`() {
        assertFailure {
            appDefinition {
                add(entry("a", RouteA))
                ads {
                    placement("top", underTab = RouteA)
                    placement("bottom", underTab = RouteA)
                }
            }
        }.isInstanceOf<IllegalArgumentException>()
            .hasMessage("placements 'top' and 'bottom' are both under RouteA; a tab has one banner")
    }

    @Test fun `the placement under the current tab root is the one the shell draws`() {
        assertThat(withHome.tabPlacementFor { it == RouteA }).isEqualTo("home")
        assertThat(withHome.tabPlacementFor { it == RouteB }).isNull()
        assertThat(withHome.tabPlacementFor { false }).isNull()
    }

    @Test fun `a placement without a tab is never drawn by the shell`() {
        val definition = appDefinition {
            add(entry("a", RouteA))
            ads { placement("own") }
        }
        assertThat(definition.tabPlacementFor { true }).isNull()
    }

    // The startup checks against the build's settings.

    @Test fun `ads() without the setting fails in every build type`() {
        listOf(true, false).forEach { debugBuild ->
            assertFailure { adsWiring(withHome, declared = false, mode = "off", units = "", contributorPresent = false, debugBuild = debugBuild) }
                .isInstanceOf<IllegalStateException>()
                .hasMessage("ads { } needs ads.enabled=true or debug in app.properties")
        }
    }

    @Test fun `the setting without ads() fails`() {
        assertFailure {
            adsWiring(appDefinition { add(entry("a", RouteA)) }, declared = true, mode = "on", units = "", contributorPresent = true, debugBuild = true)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("ads.enabled is set but the app definition doesn't call ads { }")
        // ads.enabled=debug in a release build: off, but still declared.
        assertFailure {
            adsWiring(appDefinition { add(entry("a", RouteA)) }, declared = true, mode = "off", units = "", contributorPresent = false, debugBuild = false)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("ads.enabled is set but the app definition doesn't call ads { }")
    }

    @Test fun `ads off in this build keeps placements inert`() {
        // ads.enabled=debug in a release build: the definition still declares its placements.
        assertThat(adsWiring(withHome, declared = true, mode = "off", units = "", contributorPresent = false, debugBuild = false))
            .isEqualTo(AdsWiring(enabled = false, units = emptyMap()))
    }

    @Test fun `an app without ads, or a missing resource, is off`() {
        val plain = appDefinition { add(entry("a", RouteA)) }
        assertThat(adsWiring(plain, declared = false, mode = "off", units = "", contributorPresent = false, debugBuild = true)).isEqualTo(AdsWiring.OFF)
        assertThat(adsWiring(plain, declared = false, mode = "", units = "", contributorPresent = false, debugBuild = false)).isEqualTo(AdsWiring.OFF)
    }

    @Test fun `on without the sdk module fails`() {
        assertFailure { adsWiring(withHome, declared = true, mode = "on", units = "home=$UNIT", contributorPresent = false, debugBuild = false) }
            .isInstanceOf<IllegalStateException>()
            .hasMessage("ads are on but :core:ads:google isn't linked")
    }

    @Test fun `on in release uses the built units`() {
        val wiring = adsWiring(withHome, declared = true, mode = "on", units = "home=$UNIT", contributorPresent = true, debugBuild = false)
        assertThat(wiring).isEqualTo(AdsWiring(enabled = true, units = mapOf("home" to UNIT)))
    }

    @Test fun `a unit without a placement fails, in release and in debug`() {
        listOf(false, true).forEach { debugBuild ->
            assertFailure {
                adsWiring(withHome, declared = true, mode = "on", units = "home=$UNIT;other=$OTHER", contributorPresent = true, debugBuild = debugBuild)
            }.isInstanceOf<IllegalStateException>()
                .hasMessage("ads.android.banner.other has no matching ads { placement(\"other\") }")
        }
    }

    @Test fun `on in release fails for a placement without a unit`() {
        assertFailure { adsWiring(withHome, declared = true, mode = "on", units = "", contributorPresent = true, debugBuild = false) }
            .isInstanceOf<IllegalStateException>()
            .hasMessage("placement 'home' has no ads.android.banner.home")
    }

    @Test fun `in debug a placement without a unit gets google's test banner`() {
        val wiring = adsWiring(withHome, declared = true, mode = "on", units = "", contributorPresent = true, debugBuild = true)
        assertThat(wiring).isEqualTo(AdsWiring(enabled = true, units = mapOf("home" to DEBUG_TEST_BANNER_UNIT)))
        assertThat(DEBUG_TEST_BANNER_UNIT).isEqualTo("ca-app-pub-3940256099942544/9214589741")
    }

    @Test fun `the units string parses to placement and unit`() {
        assertThat(parseAdsUnits("")).isEqualTo(emptyMap())
        assertThat(parseAdsUnits("home=$UNIT;b-2=$OTHER")).isEqualTo(mapOf("home" to UNIT, "b-2" to OTHER))
    }

    // The contributor is found by type: R8 renames AdsContributor in release builds.

    @Test fun `a contributor marked as the ads services is found whatever its class name`() {
        assertThat(adsContributorPresent(listOf(adsMarked))).isEqualTo(true)
    }

    @Test fun `an unmarked contributor is not the ads services`() {
        assertThat(adsContributorPresent(listOf(ModuleContributor { emptyList() }))).isEqualTo(false)
        assertThat(adsContributorPresent(emptyList())).isEqualTo(false)
    }

    // The graph.

    private val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "shell-ads-${System.nanoTime()}"
    private var app: KoinApplication? = null

    @AfterEach fun tearDown() {
        app?.close()
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    /** The foundation graph, with store files in a temporary directory instead of the app's. */
    private fun graph(definition: AppDefinition, wiring: AdsWiring) = koinApplication {
        FileSystem.SYSTEM.createDirectories(dir)
        modules(foundationModules(definition, emptyList(), ads = wiring) + module { single { StorePaths { name -> dir / "$name.json" } } })
    }.also { app = it }.koin

    @Test fun `consent is gathered when only ads are used`() {
        assertThat(withHome.needsConsent).isEqualTo(true)
        val koin = graph(withHome, AdsWiring(enabled = true, units = mapOf("home" to UNIT)))
        val consent = koin.get<Consent>()
        consent.gatherOnLaunch()
        // No consent SDK linked here: it resolves to everything denied, so no banner may load.
        assertThat(consent.state.value).isEqualTo(ConsentState.Resolved(canRequestAds = false, analyticsAllowed = false))
    }

    @Test fun `ads off in this build binds a controller that hides every placement`() {
        val koin = graph(withHome, AdsWiring(enabled = false, units = emptyMap()))
        assertThat(koin.get<AdsController>().bannerState("home").value).isEqualTo(BannerState.Hidden(HideReason.ADS_OFF))
    }

    @Test fun `an app without ads gets no ads binding`() {
        val koin = koinApplication { modules(foundationModules(appDefinition { add(entry("a", RouteA)) }, emptyList())) }
            .also { app = it }.koin
        assertThat(koin.getOrNull<AdsController>()).isNull()
    }

    private companion object {
        const val UNIT = "ca-app-pub-1234567890123456/1234567890"
        const val OTHER = "ca-app-pub-1234567890123456/0987654321"
    }
}
