package com.whitelabel.core.ads

/**
 * The platform an [adsWiring] runs on: it names the property keys in errors and gives Google's
 * public adaptive banner test unit (developers.google.com/admob/android/test-ads and
 * developers.google.com/admob/ios/test-ads, checked 2026-11-12). The build-time copy of these
 * units (and the test app IDs) is `Platform` in build-logic's `AdsSetup.kt`; keep the two in sync.
 */
enum class AdsPlatform(val bannerKeyPrefix: String, val testBannerUnit: String) {
    ANDROID("ads.android.banner.", "ca-app-pub-3940256099942544/9214589741"),
    IOS("ads.ios.banner.", "ca-app-pub-3940256099942544/2435281174"),
}

/**
 * How the ads part of the graph is wired: whether ads are on in this build, and the banner unit of
 * each declared placement (resolved for the build type).
 */
data class AdsWiring(val enabled: Boolean, val units: Map<String, String>) {
    companion object {
        val OFF = AdsWiring(enabled = false, units = emptyMap())
    }
}

/** The units setting, `placement=unit;placement=unit`, as a map; empty for none. */
fun parseAdsUnits(value: String): Map<String, String> =
    value.split(';').filter { it.isNotBlank() }.associate { pair ->
        val (placement, unit) = pair.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        placement.trim() to unit.trim()
    }

/**
 * Checks the app's ads declaration ([usesAds], with its [placements] by name) against the app's
 * setting ([declared]: `ads.enabled` isn't `false`), this build's [mode] (`on`, or `off` when ads are
 * off for the build type, as in a release build of `ads.enabled=debug`) and [units] (the units
 * setting), and resolves each placement's unit. Startup fails on an inconsistent configuration: a
 * unit needs its placement, and in a release build a placement needs its unit. In a debug build a
 * placement without a unit gets the [platform]'s test unit. With ads on, [sdkLinked] says whether
 * the platform's Google Mobile Ads piece is there; without it startup fails with [sdkMissing].
 */
fun adsWiring(
    usesAds: Boolean,
    placements: List<String>,
    declared: Boolean,
    mode: String,
    units: String,
    sdkLinked: Boolean,
    sdkMissing: String,
    debugBuild: Boolean,
    platform: AdsPlatform,
): AdsWiring {
    check(!declared || usesAds) { "ads.enabled is set but the app definition doesn't call ads { }" }
    check(declared || !usesAds) { "ads { } needs ads.enabled=true or debug in app.properties" }
    if (!usesAds || mode != "on") return AdsWiring.OFF
    check(sdkLinked) { sdkMissing }
    val built = parseAdsUnits(units)
    val names = placements.toSet()
    val prefix = platform.bannerKeyPrefix
    built.keys.firstOrNull { it !in names }?.let { error("$prefix$it has no matching ads { placement(\"$it\") }") }
    return AdsWiring(
        enabled = true,
        units = placements.associateWith { name ->
            built[name] ?: if (debugBuild) platform.testBannerUnit else error("placement '$name' has no $prefix$name")
        },
    )
}

/** The feature that owns a tab a placement names: its [id], and whether it is the sign-in gate. */
class AdTabOwner(val id: String, val isGate: Boolean)

/** The entry id of the shared Settings feature, whose tab never shows a banner. */
private const val SETTINGS_FEATURE_ID = "settings"

/**
 * Checks where a shell would draw each placement with an `underTab`: under a tab ([ownerOf] finds
 * the feature owning a route, or null), never Settings or the gate (no ads on forms or settings),
 * and one banner per tab. [routeName] names a route in messages.
 */
fun checkAdPlacementTabs(placements: List<AdPlacement>, ownerOf: (route: Any) -> AdTabOwner?, routeName: (route: Any) -> String) {
    val underTabs = placements.filter { it.underTab != null }
    underTabs.forEach { placement ->
        val route = checkNotNull(placement.underTab)
        val owner = ownerOf(route)
        require(owner != null) { "placement '${placement.name}' is under ${routeName(route)}, which isn't a tab" }
        require(owner.id != SETTINGS_FEATURE_ID && !owner.isGate) {
            "No ads under ${owner.id}: settings and sign-in screens stay ad-free"
        }
    }
    underTabs.groupBy { it.underTab }.values.firstOrNull { it.size > 1 }?.let { (first, second) ->
        throw IllegalArgumentException(
            "placements '${first.name}' and '${second.name}' are both under ${routeName(checkNotNull(first.underTab))}; a tab has one banner",
        )
    }
}

/**
 * The placement a shell draws below the current screen, if that screen is a tab's root:
 * [isCurrent] says whether a tab route is the current destination.
 */
fun tabPlacementFor(placements: List<AdPlacement>, isCurrent: (route: Any) -> Boolean): String? =
    placements.firstOrNull { placement -> placement.underTab?.let(isCurrent) == true }?.name
