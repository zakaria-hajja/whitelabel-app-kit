package com.whitelabel.core.shell.brand

import android.content.Context
import android.content.pm.ApplicationInfo
import com.whitelabel.core.ads.AdsPlatform
import com.whitelabel.core.ads.AdsServicesContributor
import com.whitelabel.core.ads.AdsWiring
import com.whitelabel.core.ads.adsWiring
import com.whitelabel.core.network.ModuleContributor
import com.whitelabel.core.shell.AppDefinition

/** Google's public adaptive banner test unit for Android (developers.google.com/admob/android/test-ads). */
internal val DEBUG_TEST_BANNER_UNIT = AdsPlatform.ANDROID.testBannerUnit

/**
 * Whether `:core:ads:google`'s contributor is among [contributors]. The shell can't reference that
 * class (release builds may not link it), and R8 renames it, so it is found by its marker type.
 */
internal fun adsContributorPresent(contributors: List<ModuleContributor>): Boolean =
    contributors.any { it is AdsServicesContributor }

/**
 * Checks the definition's `ads { }` against the app's setting ([declared]: `ads.enabled` isn't
 * `false`), this build's [mode] (`foundation_ads`) and [units] (`foundation_ads_units`), and resolves
 * each declared placement's unit: the shared [com.whitelabel.core.ads.adsWiring], with
 * `:core:ads:google` as the SDK piece.
 */
internal fun adsWiring(
    definition: AppDefinition,
    declared: Boolean,
    mode: String,
    units: String,
    contributorPresent: Boolean,
    debugBuild: Boolean,
): AdsWiring = adsWiring(
    usesAds = definition.usesAds,
    placements = definition.adPlacements.map { it.name },
    declared = declared,
    mode = mode,
    units = units,
    sdkLinked = contributorPresent,
    sdkMissing = "ads are on but :core:ads:google isn't linked",
    debugBuild = debugBuild,
    platform = AdsPlatform.ANDROID,
)
/**
 * Reads the app's `foundation_ads_declared`, `foundation_ads` and `foundation_ads_units` strings
 * (false, off and none until the app plugin writes them).
 */
internal fun adsWiringFor(context: Context, definition: AppDefinition, contributors: List<ModuleContributor>): AdsWiring {
    fun string(name: String, fallback: String): String {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return if (id == 0) fallback else context.getString(id)
    }
    return adsWiring(
        definition,
        declared = string("foundation_ads_declared", "false") == "true",
        mode = string("foundation_ads", "off"),
        units = string("foundation_ads_units", ""),
        contributorPresent = adsContributorPresent(contributors),
        debugBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
    )
}
