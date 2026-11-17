package com.whitelabel.core.ads

/**
 * The key a native placement's unit has in the units string: `homeList` → `native.homeList=ca-app-pub-…`.
 * Placement names can't hold `.`, so it never collides with a banner's key. Build-logic's
 * `AdsSetup.kt` has the same constant; keep the two in sync.
 */
const val NATIVE_UNIT_KEY_PREFIX = "native."

/**
 * The platform an [adsWiring] runs on: it names the property keys in errors and gives Google's
 * public adaptive banner and native advanced test units (developers.google.com/admob/android/test-ads
 * and developers.google.com/admob/ios/test-ads, banner checked 2026-11-12, native 2026-11-17). The
 * build-time copy of these units (and the test app IDs) is `Platform` in build-logic's `AdsSetup.kt`;
 * keep the two in sync.
 */
enum class AdsPlatform(
    val bannerKeyPrefix: String,
    val testBannerUnit: String,
    val nativeKeyPrefix: String,
    val testNativeUnit: String,
) {
    ANDROID("ads.android.banner.", "ca-app-pub-3940256099942544/9214589741", "ads.android.native.", "ca-app-pub-3940256099942544/2247696110"),
    IOS("ads.ios.banner.", "ca-app-pub-3940256099942544/2435281174", "ads.ios.native.", "ca-app-pub-3940256099942544/3986624511"),
}

/**
 * What every ad request carries. With [nonPersonalized] (`ads.nonPersonalized=true` in
 * app.properties) that is AdMob's `npa=1` extra, so Google serves only non-personalized ads whatever
 * consent allows; otherwise nothing, and personalization follows consent (UMP) and ATT.
 */
data class AdRequestPolicy(val nonPersonalized: Boolean) {
    /** The network extras for AdMob: `npa=1` when [nonPersonalized], else none. */
    val extras: Map<String, String> get() = if (nonPersonalized) mapOf("npa" to "1") else emptyMap()

    companion object {
        val DEFAULT = AdRequestPolicy(nonPersonalized = false)
    }
}

/**
 * How the ads part of the graph is wired: whether ads are on in this build, the unit (banner or
 * native, by the placement's kind) of each declared placement (resolved for the build type), what each
 * request carries, and which placements are [natives] (the controller follows their rotations).
 */
data class AdsWiring(
    val enabled: Boolean,
    val units: Map<String, String>,
    val request: AdRequestPolicy = AdRequestPolicy.DEFAULT,
    val natives: Set<String> = emptySet(),
) {
    companion object {
        val OFF = AdsWiring(enabled = false, units = emptyMap())
    }
}

/** The units setting, `placement=unit;native.placement=unit`, as a map by key; empty for none. */
fun parseAdsUnits(value: String): Map<String, String> =
    value.split(';').filter { it.isNotBlank() }.associate { pair ->
        val (placement, unit) = pair.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        placement.trim() to unit.trim()
    }

/**
 * Checks the app's ads declaration ([usesAds], with its [placements]) against the app's
 * setting ([declared]: `ads.enabled` isn't `false`), this build's [mode] (`on`, or `off` when ads are
 * off for the build type, as in a release build of `ads.enabled=debug`) and [units] (the units
 * setting), and resolves each placement's unit: a banner's under its name, a native placement's
 * under [NATIVE_UNIT_KEY_PREFIX] + its name. Startup fails on an inconsistent configuration: a unit
 * needs its placement of the same kind, and in a release build a placement needs its unit. In a
 * debug build a placement without a unit gets the [platform]'s test unit for its kind. With ads on, [sdkLinked] says whether
 * the platform's Google Mobile Ads piece is there; without it startup fails with [sdkMissing].
 * [nonPersonalized] is `ads.nonPersonalized` (see [AdRequestPolicy]).
 */
fun adsWiring(
    usesAds: Boolean,
    placements: List<AdPlacement>,
    declared: Boolean,
    mode: String,
    units: String,
    sdkLinked: Boolean,
    sdkMissing: String,
    debugBuild: Boolean,
    platform: AdsPlatform,
    nonPersonalized: Boolean = false,
): AdsWiring {
    check(!declared || usesAds) { "ads.enabled is set but the app definition doesn't call ads { }" }
    check(declared || !usesAds) { "ads { } needs ads.enabled=true or debug in app.properties" }
    if (!usesAds || mode != "on") return AdsWiring.OFF
    check(sdkLinked) { sdkMissing }
    val built = parseAdsUnits(units)
    val kinds = placements.associate { it.name to it.kind }
    built.keys.forEach { key ->
        if (key.startsWith(NATIVE_UNIT_KEY_PREFIX)) {
            val name = key.removePrefix(NATIVE_UNIT_KEY_PREFIX)
            check(kinds[name] == AdKind.NATIVE) { "${platform.nativeKeyPrefix}$name has no matching ads { native(\"$name\") }" }
        } else {
            when (kinds[key]) {
                AdKind.BANNER -> Unit
                AdKind.NATIVE -> error("${platform.bannerKeyPrefix}$key is for a banner, but '$key' is declared native")
                null -> error("${platform.bannerKeyPrefix}$key has no matching ads { placement(\"$key\") }")
            }
        }
    }
    return AdsWiring(
        enabled = true,
        units = placements.associate { placement ->
            val name = placement.name
            val unit = when (placement.kind) {
                AdKind.BANNER -> built[name] ?: if (debugBuild) platform.testBannerUnit else error("placement '$name' has no ${platform.bannerKeyPrefix}$name")
                AdKind.NATIVE -> built[NATIVE_UNIT_KEY_PREFIX + name]
                    ?: if (debugBuild) platform.testNativeUnit else error("placement '$name' has no ${platform.nativeKeyPrefix}$name")
            }
            name to unit
        },
        request = AdRequestPolicy(nonPersonalized),
        natives = placements.filter { it.kind == AdKind.NATIVE }.map { it.name }.toSet(),
    )
}

/** The feature that owns a tab a placement names: its [id], and whether it is the sign-in gate. */
class AdTabOwner(val id: String, val isGate: Boolean)

/** The entry id of the shared Settings feature, whose tab never shows a banner. */
private const val SETTINGS_FEATURE_ID = "settings"

/**
 * Checks where a shell would draw each placement with an `underTab`: under a tab ([ownerOf] finds
 * the feature owning a route, or null), never Settings or the gate (no ads on forms or settings),
 * and one banner per tab; a native placement has no `underTab`. [routeName] names a route in messages.
 */
fun checkAdPlacementTabs(placements: List<AdPlacement>, ownerOf: (route: Any) -> AdTabOwner?, routeName: (route: Any) -> String) {
    placements.firstOrNull { it.kind == AdKind.NATIVE && it.underTab != null }?.let {
        throw IllegalArgumentException("placement '${it.name}' is native: the app places it in a list, so it has no underTab")
    }
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
 * The banner placement a shell draws below the current screen, if that screen is a tab's root:
 * [isCurrent] says whether a tab route is the current destination.
 */
fun tabPlacementFor(placements: List<AdPlacement>, isCurrent: (route: Any) -> Boolean): String? =
    placements.firstOrNull { placement -> placement.kind == AdKind.BANNER && placement.underTab?.let(isCurrent) == true }?.name
