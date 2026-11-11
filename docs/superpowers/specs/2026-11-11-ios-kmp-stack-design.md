# iOS for the Whitelabel Apps — Stack Design

Status: approved by the owner (2026-11-11). Plan for steps 1–4: `docs/superpowers/plans/2026-11-11-kmp-migration.md`.
Starts **after** the Android starter (foundation, `android-starter`) is finished. Don't start the
migration before then.

## 0. Intent and the why

The owner builds a line of small whitelabel apps on one shared foundation (`android-starter`,
included in each app repo as a git submodule). Munch, the subscription tracker, is the first app
built on it. Until now every app has been Android-only, and the Munch spec lists iOS as a v1
non-goal.

**This is the start of shipping every app on both Android and iOS.** It's a permanent move, not a
one-off market test of Munch. That's why the foundation itself has to become multiplatform: any
cost paid per app gets multiplied across the whole app line.

### What success looks like

- A new whitelabel app can ship on both stores, with the rules, data and screen logic written once
  in Kotlin and only the UI written per platform.
- The rules (points, badges, feeding, totals) behave the same on both platforms because there's one
  copy of them.
- Munch is live on the App Store with the same behavior as on Android, and an iOS feel.
- Android users notice nothing during or after the migration.

### Goals

- Move the foundation's non-UI layers and ViewModels to Kotlin Multiplatform (KMP).
- Create an iOS starter: the Swift counterpart of the foundation's UI side (shell, design system,
  branding).
- Ship Munch on iOS on top of both.
- Write the iOS and interop Claude skills that match the existing `kmp-*`/`android-*` ones.

### Non-goals

- Shared UI (Compose Multiplatform). It was considered and rejected (see §1).
- Rewriting the Android UI. Android keeps Jetpack Compose.
- iPad layouts, macOS, watchOS and widgets.
- New product features. Munch on iOS matches Munch on Android.

### Anti-goals

- **Breaking or degrading Android** at any step of the migration.
- **Logic in Swift.** Swift code that computes points, dates, totals or validation duplicates the
  rules and lets them drift. Swift only renders state and sends actions.
- **A big-bang migration** that stops feature work for weeks or lands one huge foundation PR.
- **Editing the foundation from an app repo.** Foundation changes land through the foundation's own
  PRs (same rule as in Munch's `CLAUDE.md`).

## 1. Approaches

| | Approach | Trade-off |
|---|---|---|
| **A** | **KMP shared up to and including the ViewModels; native SwiftUI UI on iOS, Compose on Android** | Best iOS feel, but each app's screens and the design system are built twice |
| B | KMP + Compose Multiplatform: everything shared, including the UI | Lowest cost per app and existing Compose screens move over. iOS feel is slightly off for some system details (text fields, scroll physics, swipe-back) |
| C | Native Swift app, sharing only `:munch:domain` (or nothing) | No Kotlin in the iOS build, but every app and every rule is written twice and drifts |

**Chosen: A** (the owner's choice). B was recommended for the lowest cost per app, but the owner
values a native iOS feel. C was dropped because its cost grows with every app, which is exactly the
problem this move solves.

**Order** (the owner's choice): wait until the Android starter is finished, then migrate.

## 2. Current state (2026-11-11)

- **Foundation** (`android-starter`): modules `:core:branding, common, network, designsystem,
  datastore, shell, ui, analytics, domain, data` and `:feature:authentication, settings, profile,
  home, catalog`. Apps `globex` (with its own `notes` feature) and `showcase` live inside it, and
  the reminders kit is a separate copy.
  Everything is Android: Gradle convention plugins in `build-logic`
  (`whitelabel.android.application`, `.android.library`, `.android.library.compose`, `.hilt`,
  `.jvm.library`), and the app-level DSL in `:app` covers style packs, `firebase()`, `ads { }` and
  `reminders { }`.
- **Munch**: `:munch:domain` is a pure JVM Kotlin library (rules and tests). `:munch:data` and the
  feature modules (`creatures`, `rewards`, `settings`) are Android libraries using Hilt, the
  foundation's `JsonStore` (`:core:datastore`), `core:shell` and `core:designsystem`.
- **Android-specific code to move behind interfaces:**
  - Hilt in about 59 files (foundation + Munch).
  - About 329 `R.string`/`R.drawable` uses.
  - About 30 `android.*` imports in the foundation: Context, keystore encryption, locale, dark mode,
    Log.
  - `java.time.LocalDate` throughout Munch's domain and ViewModels.
- **ViewModels** follow MVI: an immutable `State` data class exposed as `StateFlow`, actions as
  functions or a sealed `Action`, one-shot events through a `Channel`, and `SavedStateHandle` for
  process death. UI models (`CreatureUi`, sealed `DueLabel`) are built in the ViewModel.

## 3. Target architecture

```
                  ┌───────────── shared Kotlin (KMP: commonMain) ──────────────┐
                  │  domain rules · repositories · JsonStore · network · analytics│
                  │  ViewModels (androidx.lifecycle KMP) · State / Action / Event │
                  │  UiText                                                      │
                  └───────────────┬──────────────────────────────┬──────────────┘
                     androidMain  │                              │  iosMain
              (Context, keystore, │                              │ (Keychain, NSUserDefaults /
               Android notifs…)   │                              │  file paths, UNNotifications…)
                                  ▼                              ▼
                     Jetpack Compose UI                SwiftUI UI (iOS starter + app)
                     (unchanged, koinViewModel)        observes StateFlow through SKIE
```

### 3.1 Shared Kotlin (foundation → KMP)

- **Targets:** `androidTarget`, `iosArm64` and `iosSimulatorArm64`. Builder's call: add `iosX64`
  only if an Intel Mac or CI needs it.
- **What gets shared:** everything below the UI, plus the ViewModels. That means domain rules,
  repositories, `JsonStore` (on KMP DataStore or plain files with `kotlinx-serialization`), network
  (Ktor), analytics interfaces, `UiText`, and every feature's State/Action/Event/ViewModel.
- **What stays per platform:** the UI, the app shell and navigation, the design system, and
  platform services. Platform services are notifications and scheduling, Firebase SDK calls, ads,
  backup, encryption and locale. Each one gets an interface in `commonMain` with an implementation in
  `androidMain`/`iosMain`. iOS implementations that need Apple SDKs (Firebase, AdMob) can be written
  in Swift and handed to Koin at startup.
- **DI:** Hilt → **Koin**, following the `kmp-di-koin` skill. Add a Koin `verify()` test per module
  to bring back some of the compile-time safety Hilt gave.
- **Dates:** `java.time` → **`kotlinx-datetime`** across domain, data and ViewModels.
- **Strings:** a ViewModel never holds a platform string. Errors and labels go out as `UiText` (or a
  sealed type like `DueLabel`), and each platform's UI turns them into text: Android with its string
  resources, iOS with `Localizable.xcstrings`. Builder's call: whether shared Compose resources
  (`Res.string`) replace `R.string` on Android. Android doesn't need them under approach A.
- **Lifecycle:** AndroidX `ViewModel` and `SavedStateHandle` (KMP artifacts). On iOS, each screen
  owns its ViewModel and calls `clear()` when it goes away (see §3.3).
- **Build:** new KMP convention plugins in `build-logic` (e.g. `whitelabel.kmp.library`). The
  existing Android plugins stay working while modules move. Android-only builds and CI skip iOS
  targets, so the Android dev loop doesn't slow down.

### 3.2 iOS starter (new)

A Swift counterpart of the foundation's UI side, reused by every app:

- **Shell:** app entry, tab bar and `NavigationStack` routing, Koin startup, and handing the shared
  ViewModels to screens.
- **Design system:** SwiftUI components and theme tokens matching the foundation's style packs
  (Munch uses the "Gallery" pack and `docs/design/tokens.json`), light and dark.
- **Branding per app:** name, bundle ID, colors, fonts and icons supplied by the app. This mirrors
  what `app.properties` and the `:app` DSL do on Android.
- **Bridge:** small Swift helpers that turn a shared ViewModel into something SwiftUI observes,
  using **SKIE** (StateFlow → async sequence, sealed classes → Swift enums, suspend → async).
- **Delivery:** the shared Kotlin is built as one XCFramework. Builder's call: Swift Package Manager
  vs. direct Gradle-built framework linking. The iOS starter code itself is a local Swift package.

**Foundation features on iOS** (Claude's call; the owner asked for the best option given that
every future app ships on both platforms): the iOS starter reaches **parity** with the foundation's
features. That means SwiftUI screens for `settings`, `home`, `profile`, `catalog` and `authentication` on top
of their shared ViewModels, so a new app gets them on iOS as it does on Android. They're built in
this order to keep Munch from waiting:
1. `settings` (no backend) together with the shell and design system. It's the first proof that
   the bridge works.
2. Munch iOS can start here (step 7).
3. `home`, `profile`, `catalog`, then `authentication` (needs the backend API environments on iOS). All of
   them must be done before the next app that uses them starts on iOS.

**Repo layout** (builder's call, proposal): a new `ios-starter` repo, the iOS twin of
`android-starter`, that apps include as a submodule. Each app repo (e.g. `munch`) gets an `iosApp/`
Xcode project next to its Android `app/`, so one repo holds both platforms and the shared `:munch:*`
KMP modules.

### 3.3 The Kotlin ↔ Swift boundary (rules)

- Swift reads `state` and calls actions. That's the whole contract.
- One-shot events (navigate, snackbar, celebrate) are a `Flow` collected in a SwiftUI `.task`.
- Every shared type Swift touches has a Swift-friendly shape: no generics on public ViewModel APIs,
  sealed interfaces for variants, no default arguments relied on from Swift, and money/dates passed
  as shared types with formatting helpers.
- Formatting that must match across platforms (money, "in 3 days") lives in shared Kotlin.
  Builder's call: whether date/currency display uses shared helpers or each platform's locale
  formatter, as long as both show the same values.

## 4. Order of work

Each step lands as its own PRs in the right repo, with Android green after every merge.

0. **Foundation: remove `globex`** (owner's choice, to save migration and iOS work). Delete
   `apps/globex` and `apps/globex/notes` with their tests. Rewrite the worked examples in
   `docs/creating-an-app.md` and `docs/adding-a-shared-feature.md` to point at Munch (a standalone
   app with its own features) or `showcase`. Update `docs/architecture.md`. The backend's `globex`
   tenant seed can stay; it's harmless.
1. **Foundation: KMP build setup.** Add KMP convention plugins and the iOS targets. Nothing moves
   yet.
2. **Foundation: Hilt → Koin**, module by module, still Android-only. Android tests stay green.
3. **Foundation: move non-UI modules to KMP** (`common`, `domain`, `data`, `datastore`, `network`,
   `analytics`, `branding` config), each with its platform interfaces and iOS implementations.
4. **Foundation: move the feature ViewModels to shared code** (`authentication`, `settings`,
   `profile`, `home`, `catalog`). Compose screens stay in `androidMain` or Android modules.
5. **iOS starter:** shell, design system (with the Gallery pack first), bridge, branding, local iOS check script, then the foundation feature screens (§3.2). The
   `showcase` app on iOS proves it end to end.
6. **Munch: bump the foundation and migrate.** `:munch:domain` → KMP (`java.time` →
   `kotlinx-datetime`), then `:munch:data`, then the ViewModels of `creatures`, `rewards` and
   `settings`. Android Munch must behave identically, checked by its existing unit and instrumented
   tests.
7. **Munch iOS:** SwiftUI screens matching `docs/design/` (Home, quick-pick, add/edit, detail,
   release, stage-up, rewards, settings), then the platform services the Android roadmap adds
   (reminders, Firebase, ads with App Tracking Transparency consent, iCloud backup), then App Store
   release.

**Skills** to write along the way with `superpowers:writing-skills`:
- `kmp-swift-interop` and `kmp-ios-framework` during steps 1–4.
- `ios-presentation`, `ios-swiftui-components`, `ios-navigation`, `ios-module-structure` and
  `ios-ui-testing` during step 5, from the real code.
- `ios-platform-services` during step 7.

### 4.1 App repos during the migration (Claude's call)

- App repos (Munch, the reminders kit) **stay pinned to the last pre-migration foundation commit**
  until steps 1–4 are merged. No Hilt/Koin bridge and no running both DI graphs side by side. Urgent
  foundation fixes during that window get cherry-picked onto a short `release/pre-kmp` foundation
  branch if one is needed.
- `showcase` lives inside the foundation repo, so the foundation PRs migrate it. It's the only
  sample app kept: it uses every shared feature and proves the iOS starter end to end. (`globex`
  is removed in step 0.)
- The reminders kit (`android-starter-kit-reminders`, a separate copy) is out of scope for this
  project unless the owner adds it.

### 4.2 Data (Claude's call)

- **Munch has no live users during the migration**: the owner migrates before Munch goes live on
  Play. So the stored JSON format may change freely during the migration. It only needs to be
  backward-compatible from the first public release on (keep the version field).
- **No data sharing across platforms.** Android backup and iCloud backup are separate. Moving
  between an Android and an iPhone is not supported (consistent with "no accounts, no sync"). The
  stored format is the same shared Kotlin code on both platforms, but nothing depends on that.

## 5. Constraints

- The foundation is a separate repo used by several apps (`showcase`, the reminders kit, Munch). Every change has to keep all of them building.
- The foundation is changed only through its own PRs. App repos only bump the submodule pointer.
- **Timing** (owner): the migration happens before Munch goes live. Munch launches on Play after
  the migration, not before. Whatever platform modules the finished starter has (e.g. ads,
  Firebase, reminders) are migrated in step 3 like the rest.
- **Apple:** the owner believes they already have an Apple Developer account. Confirm it's active,
  and set the team ID and bundle IDs, before step 5.
- **iOS CI runs locally on the owner's Mac**, not on hosted macOS runners. iOS checks run from a
  script (e.g. `scripts/check-ios.sh`: shared tests on the iOS simulator target, Xcode build,
  XCUITests) called by the pre-push hook on macOS. GitHub CI stays Android-only (JVM and Android
  tests, lint).
- Git rules as in Munch's and the foundation's `CLAUDE.md`: Conventional Commits, branch per change,
  `scripts/check.sh` before pushing, the owner merges.
- Analytics never receives subscription names or prices, on either platform (Munch spec).

## 6. Testing

- Rules and ViewModel tests move to `commonTest` (`kotlin.test`, Turbine, fakes; see
  `kmp-unit-testing`) and run on JVM and iOS simulator targets.
- Android instrumented UI tests stay as they are.
- iOS UI: XCUITest with the Robot pattern for main flows. No logic tests in Swift, because the logic
  isn't in Swift.

## 7. Left to the builder

- Exact module split and names for the new KMP plugins, as long as Android keeps working at each step.
- SKIE vs. KMP-NativeCoroutines if SKIE blocks something (SKIE is the default).
- How the XCFramework is delivered to Xcode.
- Whether `JsonStore` sits on KMP DataStore or plain files.
- Whether Android adopts shared Compose resources.
- iOS minimum version (proposal: iOS 17, for `@Observable`).

## 8. Open

- Starts after the Android starter is finished. Revisit this doc against the finished starter
  before planning.
