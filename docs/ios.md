# iOS

Every app ships on iOS with the same logic as on Android: rules, data and ViewModels are shared
Kotlin, and the screens are SwiftUI. The Swift half of the foundation lives in `ios/`. Design:
[2026-11-11-ios-starter-design.md](superpowers/specs/2026-11-11-ios-starter-design.md).

## Setup (macOS)

- Xcode, selected: `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`, then open it
  once (or `sudo xcodebuild -runFirstLaunch`) and install an iOS simulator runtime.
- XcodeGen: `brew install xcodegen`. Xcode projects are generated from `project.yml` and not
  committed.
- Run showcase:
  ```sh
  ./gradlew :apps:showcase:shared:generateIosConfig      # app.properties → ios/Showcase/Config/*.xcconfig
  (cd ios/Showcase && xcodegen) && open ios/Showcase/Showcase.xcodeproj
  ```
- `scripts/check-ios.sh` is the iOS gate:
  - WhitelabelKit's tests on a simulator;
  - a Release build that must not contain the fake backend.

  The pre-push hook runs it on macOS when a push touches `ios/` or shared Kotlin. GitHub CI stays
  Android-only.

## How the pieces fit

| Piece | Where | What |
|---|---|---|
| The app's framework | `apps/<app>/shared` (KMP) | Exports the shared modules as one static framework named **`Shared`** (every app uses that name), with SKIE: flows become async sequences, sealed types Swift enums, suspend functions `async` |
| Entry point | `:core:ios` (`SharedGraph`) | `SharedGraph.start(platform:definition:appModules:useMockApi:)` once, then one typed accessor per shared ViewModel (`settingsViewModel()`, `resetPasswordViewModel(token:)`, …) |
| Fake backend | `:core:ios:mock` (`IosMock`) | Linked only with `-Pwhitelabel.mockApi=true`, which only the Xcode Debug configuration passes; call `IosMock.shared.enable()` before starting |
| WhitelabelKit | `ios/WhitelabelKit` | A static framework target every app's `project.yml` includes (`WhitelabelKit.yml`): the bridge (`SharedModel`, `UiTextView`), the platform pieces, and later the design system, shell and feature screens |
| App identity | `generateIosConfig` (`whitelabel.ios.app`) | Writes `Debug`/`Release.generated.xcconfig` from `app.properties`: bundle id (`.debug` in Debug), name, version, API URLs (`10.0.2.2` → `localhost`), tenant, deep-link scheme, defaults. `Info.plist` maps them to `WL*` keys, which `PlatformFactory` reads |

**Build.**
- WhitelabelKit's build phase runs Gradle's `embedAndSignAppleFrameworkForXcode` for the app's
  framework module (set by `WL_SHARED_PROJECT`), so the kit compiles against `Shared`.
- The app's own build phase runs it again, which also copies the shared strings into the app.
  Without that, messages are missing at runtime.
- Only Apple-silicon simulators are built (`EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64`).

## The bridge

- **`SharedModel<State, Action>`** (`@Observable`, main actor) holds a shared ViewModel through a
  `ViewModelHolder`. `state` follows its `StateFlow`, `send(_:)` forwards an action, and releasing
  the model clears the ViewModel. Screens never compute state themselves.
- **Messages:** `UiTextView(text)` or `await resolve(text)` give a ViewModel's `UiText` in the
  device language.
- **Swift gotchas:**
  - Write `Swift.Error`: the module exports a Kotlin `Error` type.
  - SKIE turns Kotlin enums into Swift enums. Pass one back with `.toKotlinEnum()` where a Kotlin
    interface is expected.
  - A Kotlin exception Swift can catch is declared `@Throws`; any other one aborts the app.

## Platform pieces (Swift)

- **`KeychainTokenCipher`:** AES-GCM via CryptoKit, with the key in the Keychain (after first
  unlock, this device only, not synced). Without the key, as after a restore, the session reads as
  signed out.
- **`IosLanguageRepository`:** the app's language choice, written to the app's `AppleLanguages`.
  iOS applies it at the next launch.
- **Stores:** in Application Support, `datastore/<name>.json`.
