package com.whitelabel.core.shell.brand

import android.content.Context
import android.content.pm.ApplicationInfo
import com.whitelabel.core.ads.AdsServicesContributor
import com.whitelabel.core.network.ModuleContributor
import com.whitelabel.core.shell.AppDefinition

/** Google's public adaptive banner test unit for Android (developers.google.com/admob/android/test-ads). */
internal const val DEBUG_TEST_BANNER_UNIT = "ca-app-pub-3940256099942544/9214589741"

/**
 * How the ads part of the graph is wired: whether ads are on in this build, and the banner unit of
 * each declared placement (resolved for the build type).
 */
internal data class AdsWiring(val enabled: Boolean, val units: Map<String, String>) {
    companion object {
        val OFF = AdsWiring(enabled = false, units = emptyMap())
    }
}

/**
 * Whether `:core:ads:google`'s contributor is among [contributors]. The shell can't reference that
 * class (release builds may not link it), and R8 renames it, so it is found by its marker type.
 */
internal fun adsContributorPresent(contributors: List<ModuleContributor>): Boolean =
    contributors.any { it is AdsServicesContributor }

/** The `foundation_ads_units` string, `placement=unit;placement=unit`, as a map; empty for none. */
internal fun parseAdsUnits(value: String): Map<String, String> =
    value.split(';').filter { it.isNotBlank() }.associate { pair ->
        val (placement, unit) = pair.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        placement.trim() to unit.trim()
    }

/**
 * Checks the definition's `ads { }` against this build's [mode] (`foundation_ads`: `on`, or `off`
 * when ads are off for the build type) and [units] (`foundation_ads_units`), and resolves each
 * declared placement's unit. Startup fails on an inconsistent configuration.
 *
 * `off` with `ads { }` is an error only in a debug build: there it can only mean `ads.enabled=false`,
 * while in a release build it is also `ads.enabled=debug`, whose placements just stay hidden.
 * In a debug build a placement without a unit gets Google's test unit.
 */
internal fun adsWiring(
    definition: AppDefinition,
    mode: String,
    units: String,
    contributorPresent: Boolean,
    debugBuild: Boolean,
): AdsWiring {
    val on = mode == "on"
    check(!on || definition.usesAds) { "ads.enabled is set but the app definition doesn't call ads { }" }
    if (!definition.usesAds) return AdsWiring.OFF
    check(on || !debugBuild) { "ads { } needs ads.enabled=true or debug in app.properties" }
    if (!on) return AdsWiring.OFF
    check(contributorPresent) { "ads are on but :core:ads:google isn't linked" }
    val built = parseAdsUnits(units)
    return AdsWiring(
        enabled = true,
        units = definition.adPlacements.associate { placement ->
            val name = placement.name
            name to (built[name] ?: if (debugBuild) DEBUG_TEST_BANNER_UNIT else error("placement '$name' has no ads.android.banner.$name"))
        },
    )
}

/** Reads the app's `foundation_ads` and `foundation_ads_units` strings (off and none until the app plugin writes them). */
internal fun adsWiringFor(context: Context, definition: AppDefinition, contributors: List<ModuleContributor>): AdsWiring {
    fun string(name: String, fallback: String): String {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return if (id == 0) fallback else context.getString(id)
    }
    return adsWiring(
        definition,
        mode = string("foundation_ads", "off"),
        units = string("foundation_ads_units", ""),
        contributorPresent = adsContributorPresent(contributors),
        debugBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
    )
}
