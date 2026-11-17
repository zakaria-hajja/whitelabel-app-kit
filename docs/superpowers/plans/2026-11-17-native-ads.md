# Native ads (list-item placement) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An app declares `ads { native("<name>") }` and shows one AdMob Native Advanced ad as a list item, with
the same rules as banners, a fixed policy-safe template themed only by design tokens, and no SDK code in the app.

**Architecture:** A placement gets a kind (`AdKind.BANNER` / `AdKind.NATIVE`). The rules (`AdGate`,
`AdsController.bannerState`) are reused unchanged: `Ready(unitId)` means "a native ad may load". Units stay one
`name=unit;…` string; native units travel under the key `native.<name>` so nothing new is plumbed into resources or
Info.plist. A shared, SDK-free `NativeAdRotation` (KMP) decides when to request, show, keep and destroy an ad, so
both platforms behave the same. Each platform has an SDK-free slot + item API (`:core:ads`, WhitelabelKit) and a
Google host (`:core:ads:google`, WhitelabelAds) that owns `AdLoader`, `NativeAdView` and the fixed template.

**Tech Stack:** Kotlin 2.2 KMP (`:core:ads:shared`), Android Compose + `play-services-ads` (legacy
`com.google.android.gms.ads`, as today), SwiftUI + GoogleMobileAds 13.x (Swift names as in `GoogleBannerView.swift`),
Koin, kotlin.test + AssertK, XCTest.

**Spec:** Munch cards https://trello.com/c/Fz4QNyRG (this plan) and https://trello.com/c/DsDPb2Il (the consumer).
Design inputs: Munch `.claude/dev-team/reports/2026-11-17-native-ads-architecture.md` (§1 SDK facts, §2 module
table) and `.claude/dev-team/reports/2026-11-17-native-ads-compliance.md` (§3 design rules: **binding**).
Decision: Munch ADR `.claude/dev-team/decisions/0007-home-native-ad.md`. Template look: the Designer's ad-row spec
(Munch `docs/design/`, delivered before Task 6/8; it may only change token roles and spacing, never the policy rules below).

## Global Constraints

- Apps write no SDK code. Google Mobile Ads code only in `:core:ads:google` and `ios/WhitelabelAds`.
- Every `AdGate` rule applies to native placements unchanged (consent `canRequestAds`, launch 1, ad-free, `setHidden`, ads off, ATT wait).
- `npa=1` (`ads.nonPersonalized`) on native requests too: reuse `adRequest(policy)` (Android) and `GoogleBannerHost.request(policy:)` (iOS).
- Template (compliance §3): an "Ad" chip ("Annonce" in French, existing `ads_label` / `KitStrings "Ad"`) **top-left**, ≥ 15×15 px, high contrast; AdChoices **top-right** (`ADCHOICES_TOP_RIGHT` / `.topRightCorner`), nothing drawn there; only registered asset views clickable (no `clickable` / tap gesture on the container); CTA through `NativeAdButton` (Android) / `isUserInteractionEnabled = false` on the CTA (iOS); main image/video only in `MediaView` ≥ 120×120 dp/pt, scaled to fit, never cropped; assets shown unmodified (text may truncate, never rewritten); no swipe or long-press.
- Video allowed, always muted (`VideoOptions` start muted), SDK controls only.
- Load only when the rules say `Ready`; never reload off screen or in the background; ≥ 60 s between requests per placement; an ad is shown at most 1 h after it loaded; a shown ad is never swapped mid-visit.
- The item takes no space and composes nothing until an ad has loaded; a failed load is silent (no gap, no error).
- Themed only by tokens: colors from `MaterialTheme.colorScheme` / `style.colors`, type from `MaterialTheme.typography` / the kit's fonts, spacing from `AppTheme.spacing` / `Spacing`, corners as `AppCard` uses on each platform. No app-supplied layout, no colors as literals.
- Placement names: `^[A-Za-z][A-Za-z0-9_-]*$`, unique across both kinds.
- Google's test units (developers.google.com/admob/android/test-ads, …/ios/test-ads; re-check when adding): Android native `ca-app-pub-3940256099942544/2247696110`, iOS native `ca-app-pub-3940256099942544/3986624511`. Two copies (runtime `AdsPlatform`, build-time `Platform`), kept in sync and pinned by tests.
- **Do not touch** (another branch, P7 shared return, edits them): `core/shell/.../ShellNavHost.kt`, `ShellTransitions.kt`, `core/designsystem/.../theme/Motion.kt`, `.../motion/SharedElements.kt`, `StylePackTest.kt`, `ios/WhitelabelKit/.../DesignSystem/Motion.swift`, `StylePackTests.swift`, `docs/adding-a-shared-feature.md`.
- No UI / instrumented tests (suspended by the owner). Verification is unit tests + `scripts/check.sh` + `scripts/check-ios.sh` (UI tests skipped) + the manual checklist with Google's **Native Ad Validator**.
- No Showcase changes (the sample app gets no new features). The manual target is Munch Debug after card 2/2.
- Heavy commands (`./gradlew …`, `scripts/check*.sh`, `xcodebuild`) only via `~/.claude/dev-team/bin/heavy <cmd>`.
- Conventional Commits, header ≤ 72 chars, trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. The owner merges the PR.

## Review Focus

1. **A load that finishes after the user left the list** (in-flight `AdLoader` callback after dispose / task cancel): the ad must not show in a dead slot or leak; it is kept for the next visit, or destroyed when replaced or expired. Pinned by `NativeAdRotationTest.loaded after the visit ended is kept for the next visit` and `…replaced next is destroyed`.
2. **Quick re-entries** (Home → detail → back within 60 s, or a tab flip): no new request, the same ad shows again at once (no shift). Pinned by `NativeAdRotationTest.a visit within the refresh interval reuses the ad without a request`.
3. **A cached ad past 1 h**: never shown, destroyed, and a request starts. Pinned by `NativeAdRotationTest.an ad older than an hour is destroyed and not shown`.
4. **The same name used for a banner and a native placement, or a native unit under the banner key**: fails the build / startup with a message naming the key, instead of loading a banner unit into `AdLoader`. Pinned in Task 1 (`AdsDslTest`), Task 2 (`AdsWiringTest`) and Task 3 (`AdsSetupTest`).
5. **`ads.nonPersonalized=true`**: native requests carry `npa=1`. Pinned by `AdsContributorTest.native host builds requests with the graph's policy` and `AdsServiceTests.testNativeHostUsesRequestPolicy`.

---

## File structure

| File | Responsibility |
|---|---|
| `core/ads/shared/.../AdsDsl.kt` (modify) | `AdKind`, `AdPlacement.kind`, `AdsBuilder.native(name)` |
| `core/ads/shared/.../AdsWiring.kt` (modify) | per-kind unit resolution, `AdsPlatform` native prefix/test unit, `NATIVE_UNIT_KEY_PREFIX`, tab checks skip/reject native |
| `core/ads/shared/.../NativeAdRotation.kt` (create) | `NativeAdHandle`, `NativeAdVisit`, `NativeAdRotation`, `NativeAdRotations` (per-placement, process-wide) |
| `core/ads/shared/.../AdsController.kt` (modify) | doc only: `bannerState` serves native placements too |
| `core/shell/.../brand/AdsWiring.kt` (modify) | pass placements with kinds |
| `core/ios/.../SharedGraph.kt` (modify) | replay kinds in `adsWiringFor`, pass placements with kinds |
| `build-logic/.../apps/{AdsSetup,AppConfig,AppConfigLoader,IosConfig}.kt`, `AndroidApplicationConventionPlugin.kt` (modify) | read `ads.<platform>.native.<name>`, test native units, merged units string |
| `core/ads/src/main/.../NativeAd.kt` (create) | `NativeAdHost`, `NativeAdSlot`, `rememberNativeAd`, `NativeAdItem` (no SDK) |
| `core/ads/google/.../GoogleNativeAdHost.kt` (create) | `AdLoader`, options, handle, `Item` → template |
| `core/ads/google/.../NativeAdTemplate.kt` (create) | the fixed list-item template + its size constants |
| `core/ads/google/.../compose/NativeAdViews.kt` (create) | Google's Compose utilities (Apache-2.0 header kept) |
| `core/ads/google/.../AdsContributor.kt` (modify) | bind `NativeAdHost` |
| `ios/WhitelabelKit/Sources/WhitelabelKit/Ads/NativeAd.swift` (create) | `NativeAdHost`, `NativeAds`, `NativeAdSlotModel`, `NativeAdItem` |
| `ios/WhitelabelAds/Sources/WhitelabelAds/GoogleNativeAdView.swift` (create) | `GoogleNativeAdHost`, loader delegate, `NativeAdView` built in code |
| `ios/WhitelabelAds/Sources/WhitelabelAds/AdsService.swift` (modify) | register the native host |
| `docs/ads.md`, `docs/architecture.md`, `docs/superpowers/checklists/2026-11-12-ads-manual.md`, `ios/WhitelabelKit/.../Shell/AppDefinition.swift` (doc comment) | docs |

---

### Task 1: Placement kind and the `native(name)` DSL

**Files:**
- Modify: `core/ads/shared/src/commonMain/kotlin/com/whitelabel/core/ads/AdsDsl.kt`
- Modify: `core/ads/shared/src/commonMain/kotlin/com/whitelabel/core/ads/AdsWiring.kt` (`checkAdPlacementTabs`, `tabPlacementFor`)
- Test: `core/ads/shared/src/commonTest/kotlin/com/whitelabel/core/ads/AdsDslTest.kt`, `.../AdsWiringTest.kt`

**Interfaces:**
- Produces:
  - `enum class AdKind { BANNER, NATIVE }` (named `AdKind`, not `AdFormat`: GoogleMobileAds exports a Swift `AdFormat`, and WhitelabelAds imports both).
  - `data class AdPlacement(val name: String, val underTab: Any?, val kind: AdKind)` plus `constructor(name: String, underTab: Any?) : this(name, underTab, AdKind.BANNER)` — the secondary constructor keeps Swift's `AdPlacement(name:underTab:)` compiling (Swift has no Kotlin default arguments).
  - `AdsBuilder.native(name: String)` — same name rule and uniqueness check as `placement`, across both kinds; no `underTab`.

- [ ] **Step 1: Write the failing tests** (AdsDslTest)
  - `native declares a native placement without a tab`: `AdsBuilder().apply { native("list") }.build()` == `listOf(AdPlacement("list", null, AdKind.NATIVE))`.
  - `placement stays a banner`: `placement("home")` → `kind == AdKind.BANNER`.
  - `a name is unique across kinds`: `placement("x"); native("x")` throws `IllegalArgumentException` with message `"placement 'x' is declared twice"`.
  - `native applies the name rule`: `native("1x")` throws with the existing name-rule message.
  (AdsWiringTest)
  - `a native placement under a tab is refused`: `checkAdPlacementTabs(listOf(AdPlacement("n", RouteA, AdKind.NATIVE)), …)` throws `"placement 'n' is native: the app places it in a list, so it has no underTab"`.
  - `tabPlacementFor ignores native placements`: only banner placements are returned.
- [ ] **Step 2:** Run `~/.claude/dev-team/bin/heavy ./gradlew :core:ads:shared:jvmTest`. Expected: FAIL (unresolved `AdKind` / `native`).
- [ ] **Step 3:** Implement the types above; in `checkAdPlacementTabs` reject native + `underTab` before the tab lookup; `tabPlacementFor` filters `kind == BANNER`.
- [ ] **Step 4:** Rerun. Expected: PASS, and the existing `AdsDslTest` / `AdsWiringTest` cases unchanged.
- [ ] **Step 5: Commit** `feat(ads): declare native placements in ads { }`

### Task 2: Resolve native units at startup (both platforms)

**Files:**
- Modify: `core/ads/shared/.../AdsWiring.kt` (`AdsPlatform`, `adsWiring`)
- Modify: `core/shell/src/main/kotlin/com/whitelabel/core/shell/brand/AdsWiring.kt`
- Modify: `core/ios/src/iosMain/kotlin/com/whitelabel/core/ios/SharedGraph.kt` (`adsWiringFor` only)
- Test: `core/ads/shared/.../AdsWiringTest.kt`, `core/shell/src/test/kotlin/com/whitelabel/core/shell/AdsWiringTest.kt`, `core/ios/src/iosTest/kotlin/com/whitelabel/core/ios/SharedGraphAdsTest.kt`

**Interfaces:**
- Consumes: `AdPlacement`, `AdKind` (Task 1).
- Produces:
  - `const val NATIVE_UNIT_KEY_PREFIX = "native."` — the key a native unit has in the units string (`homeList` → `native.homeList=ca-app-pub-…`). `.` is not allowed in placement names, so keys can't collide. Build-logic has the same constant (Task 3).
  - `enum class AdsPlatform(val bannerKeyPrefix: String, val testBannerUnit: String, val nativeKeyPrefix: String, val testNativeUnit: String)` with `ANDROID("ads.android.banner.", "…/9214589741", "ads.android.native.", "ca-app-pub-3940256099942544/2247696110")`, `IOS("ads.ios.banner.", "…/2435281174", "ads.ios.native.", "ca-app-pub-3940256099942544/3986624511")`.
  - `adsWiring(usesAds, placements: List<AdPlacement>, declared, mode, units, sdkLinked, sdkMissing, debugBuild, platform, nonPersonalized = false): AdsWiring` — `placements` changes from `List<String>` to `List<AdPlacement>`. `AdsWiring.units` stays `Map<placementName, unitId>` (so `AdsController` is unchanged).

- [ ] **Step 1: Write the failing tests** (shared AdsWiringTest)
  - `a native placement takes its native unit`: units `"home=u1;native.list=u2"`, placements banner `home` + native `list` → `units == mapOf("home" to "u1", "list" to "u2")`.
  - `debug native placement without a unit gets the native test unit`: → `"ca-app-pub-3940256099942544/3986624511"` (IOS in this test file's helper).
  - `release native placement without a unit fails`: message `"placement 'list' has no ads.ios.native.list"`.
  - `a native unit without a native placement fails`: units `"native.x=u"` → `"ads.ios.native.x has no matching ads { native(\"x\") }"`.
  - `a banner unit for a native placement fails`: units `"list=u"` with native `list` → `"ads.ios.banner.list is for a banner, but 'list' is declared native"`.
  - `the test units are Google's`: pin both `testNativeUnit` values (next to the existing banner pin).
  - Shell AdsWiringTest: a definition with `ads { placement("home", underTab = RouteA); native("list") }` and units `"home=u1;native.list=u2"` resolves both.
  - SharedGraphAdsTest: `declareAds(listOf(AdPlacement("list", null, AdKind.NATIVE)))` with an `on` platform and no units in a debug build resolves the iOS native test unit; the `AdsBuilder` replay keeps the kind (a native + banner with the same name fails as on Android).
- [ ] **Step 2:** Run `heavy ./gradlew :core:ads:shared:allTests :core:shell:testDebugUnitTest :core:ios:iosSimulatorArm64Test`. Expected: FAIL.
- [ ] **Step 3:** Implement: look up `name` for banners and `NATIVE_UNIT_KEY_PREFIX + name` for natives; the unknown-key check splits keys by the prefix. Shell passes `definition.adPlacements`; `SharedGraph.adsWiringFor` replays with `if (it.kind == AdKind.NATIVE) native(it.name) else placement(it.name, it.underTab)` and passes `placements`.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5: Commit** `feat(ads): resolve native ad units per platform`

### Task 3: Build-time native unit keys

**Files:**
- Modify: `build-logic/convention/src/main/kotlin/com/whitelabel/buildlogic/apps/AdsSetup.kt`, `AppConfig.kt`, `AppConfigLoader.kt`, `IosConfig.kt`, `build-logic/convention/src/main/kotlin/com/whitelabel/buildlogic/AndroidApplicationConventionPlugin.kt:123-125`
- Test: `build-logic/convention/src/test/kotlin/com/whitelabel/buildlogic/apps/AdsSetupTest.kt`, `AppConfigLoaderTest.kt`, `IosConfigTest.kt`

**Interfaces:**
- Produces:
  - `AppConfig.adsAndroidNativeUnits: Map<String, String>`, `AppConfig.adsIosNativeUnits: Map<String, String>` (from `ads.android.native.<name>` / `ads.ios.native.<name>`, same format check as banner units).
  - `Platform(appIdKey, unitKeyPrefix, testAppId, testBannerUnit, nativeUnitKeyPrefix, testNativeUnit)` — values as `AdsPlatform` (Task 2).
  - `adsSetup(mode, appId, units, nativeUnits: Map<String, String>, platform, label): AdsSetup` — `unitsFor` holds banner names plus `"native.<name>"` keys; Debug replaces each with its kind's test unit. `ON` needs at least one unit of either kind (message: `"needs at least one ads.android.banner.<placement> or ads.android.native.<placement>"`).
  - `const val NATIVE_UNIT_KEY_PREFIX = "native."` in `AdsSetup.kt` (twin of Task 2's).

- [ ] **Step 1: Write the failing tests**
  - AdsSetupTest `native units are merged under native keys`: `adsSetup(ON, appId, mapOf("home" to b), mapOf("list" to n), ANDROID, …).unitsFor["release"] == mapOf("home" to b, "native.list" to n)`; `adsUnitsString(...) == "home=$b;native.list=$n"`.
  - `debug native units are the native test unit` (both platforms; pins the two IDs).
  - `ON with only a native unit is accepted`.
  - `the same name as banner and native fails`: `InvalidAppConfigException` naming both keys.
  - AppConfigLoaderTest: `ads.android.native.list=<valid>` is read; `ads.android.native.1x` fails with the placement-name message; an invalid unit format fails like the banner key.
  - IosConfigTest: `WL_ADS_UNITS` for Debug contains `native.list=ca-app-pub-3940256099942544/3986624511`.
- [ ] **Step 2:** Run `heavy ./gradlew -p build-logic :convention:test`. Expected: FAIL.
- [ ] **Step 3:** Implement; pass `app.adsAndroidNativeUnits` / `app.adsIosNativeUnits` from the two call sites.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5: Commit** `feat(build-logic): read native ad unit keys`

### Task 4: `NativeAdRotation`, the shared load/show/keep rules

**Files:**
- Create: `core/ads/shared/src/commonMain/kotlin/com/whitelabel/core/ads/NativeAdRotation.kt`
- Modify: `core/ads/shared/.../AdsController.kt` (KDoc of `bannerState`: "also the state of a native placement: `Ready` means a native ad may load")
- Test: `core/ads/shared/src/commonTest/kotlin/com/whitelabel/core/ads/NativeAdRotationTest.kt`

**Interfaces:**
- Produces (main thread only on both platforms; no coroutines, no SDK):
  - `interface NativeAdHandle { fun destroy() }` — the platform host wraps the SDK ad (Swift classes conform to it).
  - `data class NativeAdVisit(val ad: NativeAdHandle?, val request: Boolean)`.
  - `class NativeAdRotation(private val nowMillis: () -> Long, val refreshMillis: Long = 60_000, val maxAgeMillis: Long = 3_600_000)`:
    - `fun beginVisit(): NativeAdVisit` — the list appeared with the rules at `Ready`.
    - `fun loaded(ad: NativeAdHandle): NativeAdHandle?` — returns the ad to show now, or null when it is kept for the next visit.
    - `fun failed()`
    - `fun endVisit()`
  - `object NativeAdRotations { fun of(placement: String): NativeAdRotation }` — one per placement per process, on `Clock.System`.

Rules the tests pin (the algorithm the signatures don't determine):
1. `beginVisit`: destroy any held ad older than `maxAgeMillis`. If a *next* ad is held, it becomes *current* (the old current is destroyed). `ad` = current. `request` = no load in flight **and** no next held **and** (`current == null` or current age ≥ `refreshMillis`) **and** ≥ `refreshMillis` since the last request (or none yet). `request == true` records the request time and "in flight".
2. `loaded`: clears "in flight". In a visit with nothing shown → it becomes current and is returned. Otherwise it becomes *next* (destroying a previous next) and null is returned. Never swaps a shown ad.
3. `failed`: clears "in flight"; nothing else (retry on a later visit, still ≥ 60 s after the last request).
4. `endVisit`: ends the visit; held ads stay.

- [ ] **Step 1: Write the failing tests** (fake clock `var now = 0L`, `FakeAd` recording `destroyed`)
  - `first visit requests and shows nothing`: `beginVisit() == NativeAdVisit(null, true)`.
  - `loaded during the visit shows at once`: `loaded(a) == a`.
  - `a visit within the refresh interval reuses the ad without a request`: load a, end, now = 30_000, `beginVisit() == NativeAdVisit(a, false)`.
  - `after the refresh interval the shown ad stays and a replacement is requested`: now = 61_000 → `NativeAdVisit(a, true)`; `loaded(b) == null`; `a.destroyed == false`.
  - `the replacement shows on the next visit and the old ad is destroyed`: end, begin → `ad == b`, `a.destroyed`.
  - `loaded after the visit ended is kept for the next visit`: begin (request), end, `loaded(a) == null`, begin → `ad == a`.
  - `a replaced next is destroyed`: two loads kept as next → the first is destroyed.
  - `an ad older than an hour is destroyed and not shown`: now = 3_600_001 → `ad == null`, `request == true`, `a.destroyed`.
  - `a failed load waits the refresh interval`: begin, `failed()`, end, now = 10_000, begin → `request == false`; now = 60_000 → `true`.
  - `no second request while one is in flight`: begin (request), end, now = 120_000, begin → `request == false`.
  - `rotations are one per placement`: `NativeAdRotations.of("a") === NativeAdRotations.of("a")`, `!== of("b")`.
- [ ] **Step 2:** Run `heavy ./gradlew :core:ads:shared:allTests`. Expected: FAIL.
- [ ] **Step 3:** Implement `NativeAdRotation.kt`.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5: Commit** `feat(ads): share native ad load and refresh rules`

### Task 5: Android slot and item API (`:core:ads`, no SDK)

**Files:**
- Create: `core/ads/src/main/kotlin/com/whitelabel/core/ads/NativeAd.kt`
- Test: `core/ads/src/test/kotlin/com/whitelabel/core/ads/NativeAdSlotDriverTest.kt`

**Interfaces:**
- Consumes: `AdsController.bannerState`, `NativeAdRotations`, `NativeAdHandle` (Task 4).
- Produces:
  - `interface NativeAdHost { fun load(context: Context, unitId: String, onLoaded: (NativeAdHandle) -> Unit, onFailed: () -> Unit); @Composable fun Item(ad: NativeAdHandle, modifier: Modifier) }` — `load` uses the application context; callbacks arrive on the main thread, once.
  - `@Stable class NativeAdSlot internal constructor(...) { val isLoaded: Boolean }` (snapshot-state backed; `internal var ad: NativeAdHandle?`).
  - `@Composable fun rememberNativeAd(placement: String): NativeAdSlot` — hoist it at screen level (outside the `LazyColumn`), so loading starts when the screen opens. Without an `AdsController` or a `NativeAdHost` (no SDK / no `ads { }`) it returns a slot that never loads.
  - `@Composable fun NativeAdItem(slot: NativeAdSlot, modifier: Modifier = Modifier)` — `host.Item(ad, modifier)` when loaded, else nothing.
  - `internal class NativeAdSlotDriver(rotation: NativeAdRotation, load: (unitId: String, onLoaded, onFailed) -> Unit)` with `fun ready(unitId: String)`, `fun hidden()`, `fun dispose()` and `val shown: NativeAdHandle?` — the composable's logic, pulled out so it is unit-testable on the JVM. `rememberNativeAd` drives it from the `bannerState` flow (`collectAsStateWithLifecycle`) and a `DisposableEffect` (dispose → `endVisit`).

Driver rules: `ready` begins a visit (if none) and requests when the visit says so; `hidden` stops showing (`shown = null`) and ends the visit, keeping the rotation's ads (re-shown when `Ready` returns within the limits); `dispose` ends the visit. A `loaded` callback after `dispose` goes to the rotation only (kept for next visit).

- [ ] **Step 1: Write the failing tests** (NativeAdSlotDriverTest, fake `load` capturing callbacks, real `NativeAdRotation` with a fake clock)
  - `ready requests once and shows the loaded ad`.
  - `hidden mid-visit removes the ad without destroying it, ready again shows it without a request` (within 60 s).
  - `a load finishing after dispose is not shown in the disposed slot`.
  - `a second ready with the same unit doesn't request again`.
- [ ] **Step 2:** Run `heavy ./gradlew :core:ads:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement `NativeAd.kt`. Debug-loud undeclared placement: reuse `bannerUnit`.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5: Commit** `feat(ads): add the native ad slot and item for Android`

### Task 6: Android Google host and the fixed template (`:core:ads:google`)

**Files:**
- Create: `core/ads/google/src/main/kotlin/com/whitelabel/core/ads/google/compose/NativeAdViews.kt` — copy Google's "Jetpack Compose utilities" from `googleads-mobile-android-examples` (`kotlin/advanced/JetpackComposeDemo`, commit `3a4320486c72c535f7581a5e81f8e295fa1b0dd2`): `NativeAdView`, `NativeAdHeadlineView`, `NativeAdBodyView`, `NativeAdAdvertiserView`, `NativeAdIconView`, `NativeAdMediaView`, `NativeAdCallToActionView`, `NativeAdButton`, `NativeAdAttribution` (if present). Keep the Apache-2.0 header and add `// Copied from <URL> at <commit>; changed: package, internal visibility.`
- Create: `core/ads/google/src/main/kotlin/com/whitelabel/core/ads/google/NativeAdTemplate.kt`
- Create: `core/ads/google/src/main/kotlin/com/whitelabel/core/ads/google/GoogleNativeAdHost.kt`
- Modify: `core/ads/google/src/main/kotlin/com/whitelabel/core/ads/google/AdsContributor.kt`, `core/ads/google/build.gradle.kts` (add `implementation(project(":core:designsystem"))` for `AppTheme.spacing`; no new library: `play-services-ads` already has native)
- Test: `core/ads/google/src/test/kotlin/com/whitelabel/core/ads/google/AdsContributorTest.kt`, `.../NativeAdTemplateTest.kt`

**Interfaces:**
- Consumes: `NativeAdHost`, `NativeAdHandle`, `adRequest(policy)`, `MobileAdsStart`.
- Produces:
  - `internal class GoogleNativeAdHost(val request: AdRequestPolicy) : NativeAdHost` — `load`: await `MobileAdsStart` (as the banner), then `AdLoader.Builder(appContext, unitId).forNativeAd { … }.withAdListener(onAdFailedToLoad → onFailed).withNativeAdOptions(NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT).setVideoOptions(VideoOptions.Builder().setStartMuted(true).build()).build()).build().loadAd(adRequest(request))`.
  - `internal class GoogleNativeAd(val ad: com.google.android.gms.ads.nativead.NativeAd) : NativeAdHandle` (`destroy()` → `ad.destroy()`).
  - `internal object NativeAdTemplate { val MediaSize = 120.dp; val Height: Dp; val BadgeMinSize = 16.dp; val AdChoicesInset = 24.dp }` — `Height` is fixed (the Designer's spec; until it lands, `MediaSize + 2 * 16.dp + 24.dp` = 176.dp).
  - `@Composable internal fun NativeAdTemplate(ad: NativeAd, modifier: Modifier)`.
  - `AdsContributor` binds `single<NativeAdHost> { GoogleNativeAdHost(getOrNull<AdRequestPolicy>() ?: AdRequestPolicy.DEFAULT) }` next to the banner host.

Template (one variant, "list item"; the Designer's spec may change token roles and spacing only): a fixed-height `NativeAdView` container, `surface` fill, 1 dp `outlineVariant` border, `AppCard`'s corner shape, **no** `clickable`. Top row: the "Ad" chip (`stringResource(R.string.ads_label)` from `:core:ads`, `labelSmall`, `onSurface` fill / `surface` text, ≥ `BadgeMinSize`) at top-left; the top-right `AdChoicesInset` square left empty for the SDK overlay. Body row: `NativeAdMediaView` `MediaSize`×`MediaSize`, `ImageView.ScaleType.CENTER_INSIDE`; then a column: icon (square, 40 dp, only when present), headline (`titleSmall`, `onSurface`, max 2 lines, ellipsis), advertiser or body (`bodySmall`, `onSurfaceVariant`, max 2 lines; omitted when absent, no gap), CTA as `NativeAdButton` styled outlined (`outline` border, `primary` text; never `AppPrimaryButton`), shown only when present. Spacing from `AppTheme.spacing` (`md` padding, `sm` gaps). TalkBack: the container exposes the assets; the chip is read ("Ad, <headline>").

- [ ] **Step 1: Write the failing tests**
  - AdsContributorTest `binds the native host`: the contributor's module resolves `NativeAdHost` as `GoogleNativeAdHost`.
  - `native host builds requests with the graph's policy`: with `AdRequestPolicy(nonPersonalized = true)` bound, `(get<NativeAdHost>() as GoogleNativeAdHost).request.nonPersonalized == true`.
  - NativeAdTemplateTest `policy minimums`: `MediaSize >= 120.dp`, `BadgeMinSize >= 15.dp`, `Height >= MediaSize + AdChoicesInset`.
- [ ] **Step 2:** Run `heavy ./gradlew :core:ads:google:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the host, handle, template, and the contributor binding. Register every asset view inside `NativeAdView`; set the ad last (the utilities do `setNativeAd` in their `NativeAdView`).
- [ ] **Step 4:** Rerun. Expected: PASS. Then `heavy ./gradlew :core:ads:google:lintDebug` — no new warnings.
- [ ] **Step 5: Commit** `feat(ads): render native ads with Google's SDK on Android`

### Task 7: iOS slot and item API (WhitelabelKit, no SDK)

**Files:**
- Create: `ios/WhitelabelKit/Sources/WhitelabelKit/Ads/NativeAd.swift`
- Modify: `ios/WhitelabelKit/Sources/WhitelabelKit/Shell/AppDefinition.swift` (doc comment: `AdPlacement(name: "list", underTab: nil, kind: .native)`)
- Test: `ios/WhitelabelKit/Tests/WhitelabelKitTests/NativeAdTests.swift`

**Interfaces:**
- Consumes: `AdsController.bannerState(placement:)`, `NativeAdRotations.shared.of(placement:)`, `NativeAdHandle` (Kotlin protocol via `Shared`), `AdBannerSlot` (reuse for state → load/hidden/undeclared).
- Produces:
  - `public protocol NativeAdHost { @MainActor func load(unitId: String, onLoaded: @escaping (NativeAdHandle) -> Void, onFailed: @escaping () -> Void); @MainActor func item(ad: NativeAdHandle) -> AnyView; var itemHeight: CGFloat { get } }`
  - `@MainActor public enum NativeAds { public private(set) static var host: (any NativeAdHost)?; public static func register(_:); static func unregister() }`
  - `@MainActor @Observable public final class NativeAdSlotModel { public private(set) var ad: NativeAdHandle?; public var isLoaded: Bool { ad != nil }; public init(placement: String); public func observe() async }` — `observe` follows the flow (as `AdBannerModel`), drives the rotation exactly like Android's `NativeAdSlotDriver` (`ready` / `hidden` / end on task cancel). Without a controller or a host it never loads. Nothing starts in `init` (SwiftUI builds and drops `@State` initial values).
  - `public struct NativeAdItem: View { public init(slot: NativeAdSlotModel) }` — `host.item(ad:)` at `host.itemHeight` when loaded, else `EmptyView`.

- [ ] **Step 1: Write the failing tests** (fake host recording loads; a `FakeAd: NSObject, NativeAdHandle`)
  - `testNoControllerNeverLoads`, `testNoHostNeverLoads`.
  - `testReadyRequestsOnceAndShowsLoadedAd` (drive `observe` with a test flow, as `AdBannerTests` does).
  - `testHiddenRemovesAdWithoutDestroying`.
  - `testLoadAfterCancelIsNotShown`.
- [ ] **Step 2:** Run `heavy scripts/check-ios.sh` (it already skips the UI tests). Expected: FAIL. Confirm in its output that `NativeAdTests` ran (the Showcase scheme's test plan must include the new file's target; if `WhitelabelAdsTests` isn't in it, run `heavy xcodebuild test` on that package scheme as `AdsServiceTests` is run today).
- [ ] **Step 3:** Implement `NativeAd.swift`.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5: Commit** `feat(ios): add the native ad slot and item to WhitelabelKit`

### Task 8: iOS Google host and template (WhitelabelAds)

**Files:**
- Create: `ios/WhitelabelAds/Sources/WhitelabelAds/GoogleNativeAdView.swift`
- Modify: `ios/WhitelabelAds/Sources/WhitelabelAds/AdsService.swift` (`install`: `NativeAds.register(GoogleNativeAdHost(policy: Self.requestPolicy(info)))`)
- Test: `ios/WhitelabelAds/Tests/WhitelabelAdsTests/AdsServiceTests.swift`, `.../NativeAdTemplateTests.swift`

**Interfaces:**
- Consumes: `NativeAdHost`, `NativeAds` (Task 7), `GoogleBannerHost.request(policy:)`, `StartOnce.mobileAds`.
- Produces:
  - `struct GoogleNativeAdHost: NativeAdHost { let policy: AdRequestPolicy }` — `load`: await `StartOnce.mobileAds`, then `AdLoader(adUnitID:rootViewController:adTypes: [.native], options: [NativeAdViewAdOptions (preferredAdChoicesPosition = .topRightCorner), VideoOptions (startMuted = true)])`, delegate keeps itself alive until its one callback, `load(GoogleBannerHost.request(policy: policy))`. Root view controller: the key window's root (`UIApplication.shared.connectedScenes`), as the SDK needs one to present the click-through.
  - `final class GoogleNativeAd: NSObject, NativeAdHandle` wrapping `NativeAd` (`destroy()` drops the reference; the iOS SDK has no destroy).
  - `enum NativeAdTemplate { static let mediaSize: CGFloat = 120; static let height: CGFloat; static let badgeMinSize: CGFloat = 16; static let adChoicesInset: CGFloat = 24 }` — same values as Android.
  - `UIViewRepresentable` building a `NativeAdView` in code (no xib): the same layout and token roles as Android's template (`style.colors.surface`, `outlineVariant`, `onSurface`, `onSurfaceVariant`, `outline`, `primary`; `Spacing.md` / `sm`; `AppCard`'s corner). Register `headlineView`, `bodyView`/`advertiserView`, `iconView`, `mediaView` (`contentMode = .scaleAspectFit`), `callToActionView` (`isUserInteractionEnabled = false`), set `nativeAdView.nativeAd = ad` **last**. Fixed frame `height`.

- [ ] **Step 1: Write the failing tests**
  - `testInstallRegistersNativeHost`: `install(mode: "on", info: valid)` → `NativeAds.host is GoogleNativeAdHost`.
  - `testNativeHostUsesRequestPolicy`: `WLAdsNonPersonalized = yes` → the registered host's `policy.nonPersonalized == true`.
  - `testOffInstallsNoNativeHost`.
  - NativeAdTemplateTests: the three policy minimums (as Android) and `height == Android's Height` (literal pinned on both sides).
- [ ] **Step 2:** Run `heavy scripts/check-ios.sh` (UI tests skipped). Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5: Commit** `feat(ios): render native ads with Google's SDK on iOS`

### Task 9: Docs, manual checklist, full gate

**Files:**
- Modify: `docs/ads.md`, `docs/architecture.md` (rows `:core:ads`, `:core:ads:google`, `:core:ads:shared`), `docs/superpowers/checklists/2026-11-12-ads-manual.md`

- [ ] **Step 1: `docs/ads.md`**
  - Intro: replace "Banners only." with "Banners, and one native list item per placement."; house rule 7 becomes "Banners and native ads only: there's no interstitial or rewarded API."
  - Keys table: `ads.android.native.<placement>`, `ads.ios.native.<placement>` (same format; console: Ad units > Add ad unit > **Native advanced**). Test-unit table: a "Native advanced" column with the two IDs.
  - New section **Native ads**: declare (`ads { native("homeList") }` / `AdPlacement(name: "homeList", underTab: nil, kind: .native)`), place (`val ad = rememberNativeAd("homeList")` at screen level + `NativeAdItem(ad)` as a keyed list item shown only when `ad.isLoaded`; iOS `@State var ad = NativeAdSlotModel(placement:)`, `.task { await ad.observe() }`, `NativeAdItem(slot:)`), the refresh rules (Task 4), the fixed template and what apps may theme (tokens only), the policy rules the app still owns (not first in a list, ≥ 16 dp from neighbouring buttons, never styled like content, not on screenshots unless labelled), and "the Native Ad Validator appears on test ads in Debug".
- [ ] **Step 2: manual checklist** — add a "Native" section: on a test unit in Debug, Android and iOS, light and dark, French: the "Ad" chip reads and is ≥ 15 px; AdChoices top-right and uncovered; only headline/media/icon/CTA open the ad (tap the padding: nothing); muted video plays in the 120 dp media; a failed load (airplane mode) leaves no gap; re-enter within 60 s: same ad, no reload; after 60 s: same ad, new one on the next entry; ad-free on → the item disappears. **Google's Native Ad Validator** (shown over test native ads in Debug): open its panel on both platforms, it must report no errors; screenshot it for the PR. Note: Android can hide it with manifest `com.google.android.gms.ads.flag.NATIVE_AD_DEBUGGER_ENABLED=false` — don't, for QA.
- [ ] **Step 3:** `heavy scripts/check.sh` → PASS; `heavy scripts/check-ios.sh` (UI tests skipped) → PASS; `heavy scripts/verify-apps.sh` → PASS (Showcase unchanged: banner only).
- [ ] **Step 4: Commit** `docs(ads): document native list-item ads`
- [ ] **Step 5:** Push `feat/native-ads`, open the PR from the template; body: what changed, "UI tests suspended; verified by unit tests + manual checklist on Munch Debug after the bump (card 2/2)", Compliance check required (ads). **The owner merges.**
