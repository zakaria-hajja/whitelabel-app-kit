package com.whitelabel.buildlogic.apps

/** The platform an [adsSetup] is for: it picks the test IDs and the property keys named in errors. */
enum class Platform(
    val appIdKey: String,
    val unitKeyPrefix: String,
    val testAppId: String,
    val testBannerUnit: String,
    val nativeUnitKeyPrefix: String,
    val testNativeUnit: String,
) {
    // Google's public test IDs (https://developers.google.com/admob/android/test-ads and .../ios/test-ads;
    // native advanced checked 2026-11-17). The runtime copy of the banner and native units is AdsPlatform
    // in core/ads; keep the two in sync.
    ANDROID(
        "ads.android.appId", "ads.android.banner.", "ca-app-pub-3940256099942544~3347511713", "ca-app-pub-3940256099942544/9214589741",
        "ads.android.native.", "ca-app-pub-3940256099942544/2247696110",
    ),
    IOS(
        "ads.ios.appId", "ads.ios.banner.", "ca-app-pub-3940256099942544~1458002511", "ca-app-pub-3940256099942544/2435281174",
        "ads.ios.native.", "ca-app-pub-3940256099942544/3986624511",
    ),
}

/**
 * The key a native placement's unit has in the units string (`native.<placement>=unit`). Placement
 * names can't hold `.`, so it never collides with a banner's key. The runtime twin is
 * `NATIVE_UNIT_KEY_PREFIX` in core/ads; keep the two in sync.
 */
const val NATIVE_UNIT_KEY_PREFIX = "native."

/**
 * What one app gets for ads on one platform. [dependencyConfiguration] is the Gradle configuration
 * for the Google Mobile Ads module (`null` for none). Everything else is per build type
 * (`debug`/`release`): [modeFor] is `on` or `off` (`foundation_ads` on Android, `WL_ADS` on iOS),
 * [unitsFor] the unit per key: a banner's under its placement, a native one's under
 * `native.<placement>` (the units string is [adsUnitsString]), and
 * [admobAppIdFor] the AdMob app ID to publish, present only for build types where ads are on.
 * [declared] is true when `ads.enabled` isn't `false` (`foundation_ads_declared`, the same in every
 * build type), so the shell can tell `ads { }` without the setting from a build type with ads off.
 */
data class AdsSetup(
    val dependencyConfiguration: String?,
    val modeFor: Map<String, String>,
    val unitsFor: Map<String, Map<String, String>>,
    val admobAppIdFor: Map<String, String>,
) {
    val declared: Boolean get() = dependencyConfiguration != null
}

/**
 * Decides the ads setup from `ads.enabled` ([mode]), the platform's AdMob [appId], its banner
 * [units] (placement to unit ID, from `<platform>.banner.<placement>`) and its [nativeUnits]
 * (from `<platform>.native.<placement>`). Placements are known only to the app definition at
 * runtime, so the build can't check that each one has a unit; it checks that a `true` app has an
 * app ID and at least one unit of either kind (Release needs one), and that no placement name has
 * both a banner and a native unit (a name is one kind).
 * - `false`: off everywhere.
 * - `true`: on in both build types. Release uses the real units; Debug replaces every unit with
 *   Google's test unit.
 * - `debug`: on in Debug with test units, off in Release, and no ID is needed. Debug keeps a real
 *   app ID when there is one (so the consent message is found) and otherwise uses the test one.
 */
fun adsSetup(
    mode: ServiceMode,
    appId: String?,
    units: Map<String, String>,
    nativeUnits: Map<String, String>,
    platform: Platform,
    label: String,
): AdsSetup {
    val none = emptyMap<String, String>()
    if (mode != ServiceMode.OFF) {
        val clashes = units.keys.intersect(nativeUnits.keys).sorted()
        if (clashes.isNotEmpty()) {
            throw InvalidAppConfigException(
                clashes.map { name ->
                    "$label: ${platform.unitKeyPrefix}$name and ${platform.nativeUnitKeyPrefix}$name name the same placement; " +
                        "a placement is a banner or a native ad, not both"
                },
            )
        }
    }
    val realUnits = units + nativeUnits.mapKeys { NATIVE_UNIT_KEY_PREFIX + it.key }
    val testUnits = units.mapValues { platform.testBannerUnit } +
        nativeUnits.keys.associate { NATIVE_UNIT_KEY_PREFIX + it to platform.testNativeUnit }
    return when (mode) {
        ServiceMode.OFF -> AdsSetup(
            null, mapOf("debug" to "off", "release" to "off"), mapOf("debug" to none, "release" to none), emptyMap(),
        )
        ServiceMode.ON -> {
            val problems = buildList {
                if (appId.isNullOrBlank()) add("$label: ads.enabled=true needs ${platform.appIdKey}")
                if (realUnits.isEmpty()) {
                    add("$label: ads.enabled=true needs at least one ${platform.unitKeyPrefix}<placement> or ${platform.nativeUnitKeyPrefix}<placement>")
                }
            }
            if (problems.isNotEmpty()) throw InvalidAppConfigException(problems)
            AdsSetup(
                "implementation", mapOf("debug" to "on", "release" to "on"),
                mapOf("debug" to testUnits, "release" to realUnits),
                mapOf("debug" to appId!!, "release" to appId),
            )
        }
        ServiceMode.DEBUG -> AdsSetup(
            "debugImplementation", mapOf("debug" to "on", "release" to "off"),
            mapOf("debug" to testUnits, "release" to none),
            // iOS links GoogleMobileAds in every configuration (SPM can't drop it per configuration), and the SDK
            // can stop the app at launch without an app ID, so Release carries the real ID or the test one there.
            // Android Release has no SDK linked, so it publishes none.
            (appId?.takeIf { it.isNotBlank() } ?: platform.testAppId).let { id ->
                if (platform == Platform.IOS) mapOf("debug" to id, "release" to id) else mapOf("debug" to id)
            },
        )
    }
}

/** `key=unit;key=unit` (a key is a placement or `native.<placement>`), sorted by key; empty for none. Placement names never contain `=` or `;`. */
fun adsUnitsString(units: Map<String, String>): String =
    units.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" }

/**
 * The AdMob app ID that goes in the manifest or Info.plist for [buildType]. When ads are on for
 * that build type it is the one [adsSetup] resolved, which Firebase's consent SDK shares (one
 * `meta-data` element for both). Otherwise it is the plain app ID that Firebase's consent needs,
 * or an empty string so merging never fails.
 */
fun resolvedAdmobAppId(ads: AdsSetup, appId: String?, buildType: String): String =
    ads.admobAppIdFor[buildType] ?: admobManifestPlaceholder(appId)

/**
 * The ads resources the Android shell reads for [buildType]: `foundation_ads` (`on`/`off`),
 * `foundation_ads_declared`, `foundation_ads_units` and `foundation_ads_non_personalized`
 * (`ads.nonPersonalized`, published as `true` only where ads are on, like iOS's
 * `WL_ADS_NON_PERSONALIZED`).
 */
fun androidAdsResValues(ads: AdsSetup, nonPersonalized: Boolean, buildType: String): Map<String, String> {
    val on = ads.modeFor.getValue(buildType) == "on"
    return mapOf(
        "foundation_ads" to ads.modeFor.getValue(buildType),
        "foundation_ads_declared" to ads.declared.toString(),
        "foundation_ads_units" to adsUnitsString(ads.unitsFor.getValue(buildType)),
        "foundation_ads_non_personalized" to (on && nonPersonalized).toString(),
    )
}
