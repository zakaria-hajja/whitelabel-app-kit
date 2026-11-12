# Testing

| Kind | Where | Runs in | Tools |
|---|---|---|---|
| Unit (JVM) | `src/test` of each module | `scripts/check.sh`, CI | JUnit 5, AssertK, Turbine, `kotlinx-coroutines-test` |
| Unit (KMP) | `src/commonTest` (JVM and iOS), `src/jvmTest`, `src/iosTest` of each KMP module | `scripts/check.sh`, CI (JVM only; iOS on a Mac with Xcode) | `kotlin.test`, AssertK, Turbine, `kotlinx-coroutines-test` |
| Android device (KMP) | `src/androidDeviceTest`: only code that needs a `Context` or the Keystore | locally on an emulator | `kotlin.test` on JUnit 4, AssertK |
| Build logic | `build-logic/convention/src/test` | `scripts/check.sh`, CI | JUnit 5, AssertK, temp directories |
| Instrumented (Compose UI, app end-to-end) | `src/androidTest` | locally, on an emulator no other run is using; CI job postponed until after the KMP migration | `createAndroidComposeRule`, AssertK |
| Swift (WhitelabelKit) | `ios/WhitelabelKit/Tests` | `scripts/check-ios.sh` (macOS; pre-push) | XCTest, hosted by showcase's iOS app |
| Swift (Firebase and consent) | `ios/WhitelabelFirebase/Tests/WhitelabelFirebaseTests` | `scripts/check-ios.sh` | XCTest, hosted by showcase's iOS app |
| App identity | `apps/<name>/src/test/.../<Name>IdentityTest.kt` | `scripts/check.sh`, CI | asserts every `BuildConfig` field for both build types |
| Built APKs | `scripts/verify-apps.sh` | CI after `assembleDebug`; release APKs in the release build (below) | `aapt2`: package, label, version, no foreign app IDs, distinct icons |

**CI shortcuts.** CI runs as parallel jobs: `changes` (docs-only detection and the commit-message
check), then `tests` (contract check, unit tests and lint) beside `build` (debug apps and their
checks, standalone fixture). A final `ci` job collects their results and is the one required check. A PR
that changes only docs (`*.md`, `docs/**`, the PR template, `.run/`) skips `tests` and `build`;
`scripts/ci-needs-build.sh` decides. The Gradle build cache is on (`org.gradle.caching`): CI
restores it from `main`, so modules a PR doesn't touch aren't rebuilt or retested. Runs on `main`
are never cancelled, because a cancelled run doesn't save that cache.

**Release build on demand.** Release builds (R8) and their checks (`verify-apps.sh release`, and no
mock backend even with `-Pwhitelabel.mockApi=true`) run in `.github/workflows/release.yml`. They
run on every push to `main`, and on a pull request only once it has the `release-build` label.
Adding the label starts the run, and later pushes to that PR rerun it. Add the label when a PR
touches R8/ProGuard rules, dependencies, `build-logic`, the mock-backend wiring or anything
release-only. The release check isn't required, so read its result before merging a labelled PR.

**Firebase and consent suites** (no Firebase project needed; they use fake ports and a fake clock):
- `:core:consent:shared`: the state mapping from UMP statuses, asking once per launch, the TCF
  storage purpose (`ConsentTest`, `TcfTest`); `:core:consent:google`: `UmpStatusMappingTest`.
- `:core:firebase:shared`: typed reads and fallbacks, `changes` (`SourceRemoteValuesTest`), nothing
  collected before consent (`CollectionTest`, `ConsentGatedAnalyticsTest`), the mode wiring
  (`FirebaseKoinTest`); `:core:firebase:google`: the crash rule and fetch interval
  (`CrashRuleTest`, `FirebaseContributorTest`).
- `:core:domain`: `DefaultsOnlyRemoteValuesTest`. `:core:shell`: `FirebaseWiringTest`,
  `DebugMarkersTest`, `PrivacyChoicesTest`. `:core:ios`: `SharedGraphFirebaseTest`.
- Build logic: `FirebaseSetupTest`, `IosConfigTest`, `AppConfigLoaderTest` (the `true` without a
  config file fails, `debug` leaves release alone). `scripts/verify-apps.sh` checks that a release
  dex has no Firebase unless `firebase.enabled=true`.
- Swift: `WhitelabelFirebaseTests` (`FirebaseServiceTests`, `FirebaseSetupTests`, `CrashRuleTests`)
  and WhitelabelKit's `AppServiceTests`, `LaunchFlagsTests`, `PrivacyChoicesRowTests`.
- The checks that need real config files are manual:
  [the Firebase checklist](superpowers/checklists/2026-11-12-firebase-manual.md).

**Ads suites** (no AdMob account; fake consent, clock and tracking):
- `:core:ads:shared`: the rules, launch counting, the ad-free window, the controller
  (`AdGateTest`, `LaunchCounterTest`, `AdFreeTest`, `AdsControllerTest`), and the wiring and placement
  checks shared with iOS (`AdsWiringTest`, `AdsDslTest`).
- `:core:ads` and `:core:ads:google`: `AdsDslTest`, `AdBannerLogicTest`, `StartOnceTest`,
  `AdsContributorTest`. `:core:shell`: `AdsWiringTest`. `:core:ios`: `SharedGraphAdsTest`.
- Build logic: `AdsSetupTest`, `AppConfigLoaderTest`, `IosConfigTest`. `scripts/verify-apps.sh` checks
  that a release dex has no AdMob SDK unless `ads.enabled=true`.
- Swift: `WhitelabelAdsTests` (a non-hosted bundle in showcase's project) and WhitelabelKit's
  `AdBannerTests`.
- Real banners, consent and ATT are manual:
  [the ads checklist](superpowers/checklists/2026-11-12-ads-manual.md).

**UI tests are paused** (owner's decision). `scripts/check-ios.sh` skips `ShowcaseUITests` and no
new UI or end-to-end tests are written for Firebase until the pause ends.

## Conventions

These follow the project's Android skills:
- **Fakes over mocks:** an in-memory fake of the interface (e.g. `FakeNotesStore`).
- **Coroutines:** `Dispatchers.setMain(UnconfinedTestDispatcher())` for ViewModels, and Turbine for
  `StateFlow`s.
- **Unit tests stay in memory.** Storage tests are the exception. In `commonTest` they use okio's
  `FakeFileSystem` (`JsonStore(path, …, fileSystem)`), and in `jvmTest` a JUnit `@TempDir`.
- **Which source set:** shared logic in `commonTest`, which runs on the JVM and iOS. Contract tests
  (they use the JVM-only `:core:testing`), Koin `verify()` and tests built on JVM APIs go in
  `jvmTest`.
- **Network tests** use Ktor `MockEngine` via `HttpClientFactory`, so they exercise the real
  headers, envelope and error mapping.
- **Koin graph:** each Koin module has a `verify()` test (`module.verify(extraTypes = …)`, listing
  the types another module or the platform provides). On a device, each app's graph test calls
  `GlobalContext.get().assertEveryViewModelResolves()` (`:core:testing-android`) on the real graph.
- **Device tests of an app** start with `RuleChain.outerRule(FreshAppRule()).around(compose)`: a
  fresh Koin graph, no stored data, no Keystore key. They read the `FakeBackend` from the graph
  through a getter, since every test gets a new one.
- **Write the test first** and watch it fail before the implementation exists.
- **Name tests by behaviour:** `` `blank or whitespace draft is not added` ``. In KMP tests, leave
  out `, . ; [ ] / < > :`: Kotlin/Native rejects them in names.

## Commands

```sh
scripts/check.sh                                  # Gradle check for every module + build-logic tests
./gradlew :feature:catalog:testDebugUnitTest      # one module
./gradlew -p build-logic :convention:test         # the app config loader
./gradlew :core:common:allTests                   # a KMP module: jvmTest, plus iosSimulatorArm64Test on a Mac
./gradlew :core:presentation:connectedAndroidDeviceTest   # a KMP module's Android device tests

# Instrumented: start an emulator first
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1 -no-window -no-audio -no-boot-anim &
adb wait-for-device
./gradlew :core:designsystem:connectedDebugAndroidTest :core:shell:connectedDebugAndroidTest
./gradlew :apps:showcase:connectedDebugAndroidTest   # end to end, against the in-memory backend

./gradlew assembleDebug assembleRelease && scripts/verify-apps.sh
```

iOS tests need Xcode (not only the Command Line Tools) and run only on macOS:
`sudo xcode-select -s /Applications/Xcode.app/Contents/Developer` once. On Linux, iOS tasks are
skipped (`kotlin.native.ignoreDisabledTargets`).

If lint ever fails with "Unexpected failure during lint analysis", that is stale incremental lint
data, not your code. Delete `<module>/build/intermediates/lint*` and run again.
