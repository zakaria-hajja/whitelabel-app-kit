# Testing

| Kind | Where | Runs in | Tools |
|---|---|---|---|
| Unit (JVM) | `src/test` of each module | `scripts/check.sh`, CI | JUnit 5, AssertK, Turbine, `kotlinx-coroutines-test` |
| Build logic | `build-logic/convention/src/test` | `scripts/check.sh`, CI | JUnit 5, AssertK, temp directories |
| Instrumented (Compose UI, app end-to-end) | `src/androidTest` | locally, on an emulator no other run is using; CI job postponed until after the KMP migration | `createAndroidComposeRule`, AssertK |
| App identity | `apps/<name>/src/test/.../<Name>IdentityTest.kt` | `scripts/check.sh`, CI | asserts every `BuildConfig` field for both build types |
| Built APKs | `scripts/verify-apps.sh` | CI after `assembleDebug assembleRelease` | `aapt2`: package, label, version, no foreign app IDs, distinct icons |

**CI shortcuts.** A PR that changes only docs (`*.md`, `docs/**`, the PR template, `.run/`) skips
the Gradle steps; `scripts/ci-needs-build.sh` decides, and the commit-message check always runs.
The Gradle build cache is on (`org.gradle.caching`): CI restores it from `main`, so modules a PR
doesn't touch aren't rebuilt or retested.

## Conventions

These follow the project's Android skills:
- **Fakes over mocks:** an in-memory fake of the interface (e.g. `FakeNotesStore`).
- **Coroutines:** `Dispatchers.setMain(UnconfinedTestDispatcher())` for ViewModels, and Turbine for
  `StateFlow`s.
- **Unit tests stay in memory.** Storage tests (`JsonStore`, `NotesStore`) are the exception: they
  use a JUnit `@TempDir`.
- **Network tests** use Ktor `MockEngine` via `HttpClientFactory`, so they exercise the real
  headers, envelope and error mapping.
- **Write the test first** and watch it fail before the implementation exists.
- **Name tests by behaviour:** `` `blank or whitespace draft is not added` ``.

## Commands

```sh
scripts/check.sh                                  # Gradle check for every module + build-logic tests
./gradlew :feature:catalog:testDebugUnitTest      # one module
./gradlew -p build-logic :convention:test         # the app config loader

# Instrumented: start an emulator first
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1 -no-window -no-audio -no-boot-anim &
adb wait-for-device
./gradlew :core:designsystem:connectedDebugAndroidTest :core:shell:connectedDebugAndroidTest
./gradlew :apps:showcase:connectedDebugAndroidTest   # end to end, against the in-memory backend

./gradlew assembleDebug assembleRelease && scripts/verify-apps.sh
```

If lint ever fails with "Unexpected failure during lint analysis", that is stale incremental lint
data, not your code. Delete `<module>/build/intermediates/lint*` and run again.
