package com.whitelabel.buildlogic.apps

/** The platform an [adsSetup] is for: it picks the test IDs and the property keys named in errors. */
enum class Platform(val appIdKey: String, val unitKeyPrefix: String, val testAppId: String, val testBannerUnit: String) {
    // Google's public test IDs (https://developers.google.com/admob/android/test-ads and .../ios/test-ads).
    ANDROID("ads.android.appId", "ads.android.banner.", "ca-app-pub-3940256099942544~3347511713", "ca-app-pub-3940256099942544/9214589741"),
    IOS("ads.ios.appId", "ads.ios.banner.", "ca-app-pub-3940256099942544~1458002511", "ca-app-pub-3940256099942544/2435281174"),
}

/**
 * What one app gets for ads on one platform. [dependencyConfiguration] is the Gradle configuration
 * for the Google Mobile Ads module (`null` for none). Everything else is per build type
 * (`debug`/`release`): [modeFor] is `on` or `off` (`foundation_ads` on Android, `WL_ADS` on iOS),
 * [unitsFor] the banner unit per placement (the units string is [adsUnitsString]), and
 * [admobAppIdFor] the AdMob app ID to publish, present only for build types where ads are on.
 */
data class AdsSetup(
    val dependencyConfiguration: String?,
    val modeFor: Map<String, String>,
    val unitsFor: Map<String, Map<String, String>>,
    val admobAppIdFor: Map<String, String>,
)

/**
 * Decides the ads setup from `ads.enabled` ([mode]), the platform's AdMob [appId] and its banner
 * [units] (placement to unit ID, from `<platform>.banner.<placement>`). Placements are known only
 * to the app definition at runtime, so the build can't check that each one has a unit; it checks
 * that a `true` app has an app ID and at least one unit (Release needs one).
 * - `false`: off everywhere.
 * - `true`: on in both build types. Release uses the real units; Debug replaces every unit with
 *   Google's test unit.
 * - `debug`: on in Debug with test units, off in Release, and no ID is needed. Debug keeps a real
 *   app ID when there is one (so the consent message is found) and otherwise uses the test one.
 */
fun adsSetup(mode: ServiceMode, appId: String?, units: Map<String, String>, platform: Platform, label: String): AdsSetup {
    val testUnits = units.mapValues { platform.testBannerUnit }
    val none = emptyMap<String, String>()
    return when (mode) {
        ServiceMode.OFF -> AdsSetup(
            null, mapOf("debug" to "off", "release" to "off"), mapOf("debug" to none, "release" to none), emptyMap(),
        )
        ServiceMode.ON -> {
            val problems = buildList {
                if (appId.isNullOrBlank()) add("$label: ads.enabled=true needs ${platform.appIdKey}")
                if (units.isEmpty()) add("$label: ads.enabled=true needs at least one ${platform.unitKeyPrefix}<placement>")
            }
            if (problems.isNotEmpty()) throw InvalidAppConfigException(problems)
            AdsSetup(
                "implementation", mapOf("debug" to "on", "release" to "on"),
                mapOf("debug" to testUnits, "release" to units),
                mapOf("debug" to appId!!, "release" to appId),
            )
        }
        ServiceMode.DEBUG -> AdsSetup(
            "debugImplementation", mapOf("debug" to "on", "release" to "off"),
            mapOf("debug" to testUnits, "release" to none),
            mapOf("debug" to (appId?.takeIf { it.isNotBlank() } ?: platform.testAppId)),
        )
    }
}

/** `placement=unit;placement=unit`, sorted by placement; empty for none. Placement names never contain `=` or `;`. */
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
