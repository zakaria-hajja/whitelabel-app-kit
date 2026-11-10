# Standalone App Support: style packs, reminders, Firebase, ads — Design

Status: draft for owner approval (2026-11-10)
Extends `2026-11-10-reusable-app-foundation-design.md`. Everything there still holds unless this
document says otherwise.

## 0. Intent and the why

The owner wants to test many small apps on the Play Store quickly, earn from ads that don't put
users off, and double down on the ones that take. The first is **Munch**, a subscription tracker
where every subscription is a little creature (its own design: `munch` repo, `docs/design/`).

Munch exposed four things every such app needs and the foundation lacks:

1. **A distinctive look and motion per app** without forking components: style packs, plus
   shared-element transitions in the shell.
2. **Local reminders**: notifications scheduled for a date and time, which survive reboots.
3. **Remote tuning, usage analytics and crash reports** without running a backend: Firebase.
4. **Clean ads**: a banner with consent, house rules built in, and an ad-free window an app can
   grant.

And one structural change: **apps can live in their own repository** and still build against the
foundation's modules from source.

### Decisions the owner made (2026-11-10)

- Munch lives in its **own repo**, consuming the foundation as a **git submodule built from source**
  (approach A below). Published libraries can come later, once the foundation settles.
- Shared work goes in the foundation as **style packs** (approach B of the look discussion), not
  app-only components.
- Munch is **local-only** at launch: no backend, no accounts. Remote values, analytics and crash
  reporting come from **Firebase**.
- **Ad rules:** banner only; no full-screen ads; no ads on the first launch, on forms, settings or in
  notifications; consent (Google UMP) before the first ad request.

### Goals

- A standalone app repo builds with the foundation's modules and convention plugins, pinned to one
  foundation commit, with the same checks an in-repo app gets.
- A new app picks a style pack in one line; components and screen transitions follow it.
- Any app can schedule, cancel and reschedule local reminders through one API.
- Any app can opt into Firebase (remote values, analytics, crash reports) through its config.
- Any app can show a compliant banner and grant ad-free time, without knowing about consent.

### Non-goals

- Publishing modules to a Maven repository (later, when the foundation settles).
- Interstitial, rewarded or app-open ad formats. The ads module doesn't offer them.
- Cloud sync, accounts or a backend for standalone apps.
- Making existing apps (showcase, globex) adopt Firebase or ads.

### Anti-goals (a failure even if it works)

- Showcase or globex looking or behaving differently after this work. The default style pack is
  today's look.
- App identity (app ID, ad unit IDs, Firebase project files, colors) in shared modules.
- An ads API that makes it easy to break the house rules (an ad on first launch, an ad without
  consent, an ad in an ad-free window).
- A standalone app that silently builds against a foundation version nobody can name.

## 1. Approaches

**How a separate app repo uses the foundation**
- **A. Git submodule + source include (chosen).** The app repo contains `foundation/` (a submodule
  pinned to a commit). Its settings use `foundation/build-logic` and include the shared modules from
  `foundation/`. Foundation changes are edited in place and land through the foundation's own PRs.
- B. Published Maven artifacts. Clean versions, but every module and the convention plugin need a
  publishing setup and a release per change. Too slow while the foundation changes daily.
- C. Sibling folder without a link. No record of which foundation version an app used, and CI
  can't reproduce it. Rejected.

**Per-app look**
- A. App-owned showpiece components on today's design system. Cheapest now, but every app redoes
  its motion and component styling.
- **B. Style packs in `:core:designsystem` (chosen by the owner).** A named preset for everything
  except colors and fonts, which stay in brand resources.
- C. The app bypasses the design system. Shared screens (settings, consent, empty states) would look
  foreign. Rejected.

**Remote tuning without a backend**
- **A. Firebase Remote Config, plus Analytics and Crashlytics (chosen by the owner).** No server; a
  web console; usage numbers for the market test.
- B. Extend `backend-api`'s `app/config` with typed values. Requires hosting a backend for a
  local-only app.
- C. No remote values. Every rebalance needs a Play release.

**Reminder scheduling**
- **A. `AlarmManager.setAndAllowWhileIdle` with a persisted schedule (chosen, builder's call).**
  Fires near the requested minute even in Doze, needs no exact-alarm permission (which Play restricts
  to alarm and calendar apps), and reschedules after reboot.
- B. WorkManager one-time work. Simpler, but it can run hours late under Doze, which is wrong for a
  "today at 09:00" reminder.
- C. Exact alarms. Not allowed by Play policy for this kind of app.

## 2. Standalone apps (submodule + source include)

### Layout of an app repo

```
munch/
  foundation/                 git submodule → android-starter, pinned commit
  app/                        the app module (applies whitelabel.android.application)
    app.properties
    google-services.json      when firebase.enabled=true
    src/main/res/…            brand resources (as in-repo apps)
  feature/…                   the app's own feature modules (any names)
  settings.gradle.kts
  gradle/ (wrapper only)      the version catalog comes from foundation/
  scripts/check.sh, .githooks/, .github/workflows/ci.yml
```

### What the foundation adds

- **`gradle/foundation.settings.gradle.kts`** in the foundation: one script an app's
  `settings.gradle.kts` applies. It includes every shared module under its usual path (`:core:shell`,
  `:core:ads`, …) with `projectDir` under `foundation/`, so the modules' own `project(":core:x")`
  references resolve unchanged. It also registers the `libs` version catalog from
  `foundation/gradle/libs.versions.toml`.
- The app's `settings.gradle.kts` is then: `pluginManagement { includeBuild("foundation/build-logic") }`,
  repositories, `apply(from = "foundation/gradle/foundation.settings.gradle.kts")`, and its own
  `include(":app")` plus feature modules.
- **The app's version catalog is the foundation's.** An app that needs an extra library declares it
  directly in its module (`implementation("group:name:version")`). That's allowed for app modules
  only, and the review rule is: if two apps need it, it moves to the foundation catalog.
- **Checks:** the app plugin's existing `app.properties` validation and identity test run unchanged.
  `scripts/verify-apps.sh` gains a mode that takes an APK path, so an app repo's CI can run the same
  APK checks without the foundation's app list.
- **Foundation CI** runs a smoke build of a minimal standalone app fixture
  (`fixtures/standalone-app/`) so changes that would break submodule consumers fail in the
  foundation, not later in Munch.
- **Docs:** `docs/creating-a-standalone-app.md`, covering repo setup, the submodule workflow (push the
  foundation branch first, then bump the pointer; never point at an unpushed commit), and updating.

### Apps without a backend (call)

Today `app.properties` requires `api.debug.baseUrl` / `api.release.baseUrl`, and the shell refreshes
`app/config` from the backend at startup. A local-only app has neither.

- The `api.*` keys become **optional**. With no base URL, the shell binds no HTTP client, skips the
  `app/config` refresh (the config is the app's build-time defaults), and a definition that includes a
  feature with `requiresSignIn` or any network-backed shared feature fails to build with a message
  naming the missing key.
- In-repo apps keep their keys, so nothing changes for them.
- **Backups:** the shell's manifest declares Android backup rules that include `JsonStore` files and
  exclude the encrypted token store (its key lives in the Keystore and can't be restored). A
  local-only app's data then survives reinstalls and phone changes through the user's Google backup.

### Dependency rules (unchanged, restated for app repos)

- Shared modules never depend on any app. Apps depend on any shared module.
- App identity stays in the app repo. A foundation change that needs an app value takes it through
  `app.properties`, brand resources or the `appDefinition { … }` DSL.

## 3. Style packs (`:core:designsystem`) and shell transitions

### The model

A `StylePack` is a value object with everything about the look **except colors and fonts**:

| Part | Content |
|---|---|
| `typeScale` | sizes, line heights, weights and tracking for the M3 type roles (fonts still come from `brand_display` / `brand_body`) |
| `shapes` | small/medium/large corner sizes. **Replaces** `brand_corner_*` for apps that set a pack; the default pack reads `brand_corner_*` so existing apps don't change |
| `surfaces` | how containers separate: `Shadow` (elevation) or `Hairline` (1 dp outline, no shadow at rest); shadow levels for floating things |
| `density` | control height (48/52 dp), list row min height, screen gutter |
| `motion` | named specs: `screenEnter`, `screenExit`, `sharedElement`, `tabSwitch`, `emphasis` (spring), `snackbar`; each a duration + easing or spring parameters |
| `haptics` | which events give feedback (`confirm`, `success`, `toggle`) and the pattern for each; `none` allowed |

- `AppTheme(stylePack = …)` provides the pack through `LocalStylePack`, along with the Material
  theme built from it. Shared components (buttons, fields, cards, list rows, scaffold, dialogs,
  sheets, bottom bar, empty and error states) read it.
- **Packs at launch:** `StylePack.Material` (default, exactly today's look) and `StylePack.Gallery`
  (from the Munch design: small radii 4/8/14, hairline surfaces, larger display scale, 52 dp controls,
  and the motion and haptics on the design's Motion page). The design sessions called this the
  "Soft" pack; it is named after the chosen direction.
- An app picks one in its definition: `appDefinition { style(StylePack.Gallery) … }`. A pack can be
  copied and tweaked (`StylePack.Gallery.copy(density = …)`) for app-specific variation.
- `reducedMotion`: when the system's "remove animations" setting is on, every motion spec resolves to
  a 150 ms crossfade, and ambient animations are an app's own responsibility to stop (the pack
  exposes `LocalReducedMotion`).
- `haptics(event)` is a small helper in the design system that respects the pack and the system's
  touch-feedback setting.

### Shell transitions

- `ShellNavHost` wraps its `NavHost` in `SharedTransitionLayout` and provides the scope through a
  composition local. Each destination's `AnimatedContentScope` is exposed the same way.
- The design system gets `Modifier.sharedElement(key)` and `Modifier.sharedBounds(key)` helpers that
  read both locals and the pack's `sharedElement` spec, and do nothing when the locals are absent
  (previews, tests, a screen used outside the shell).
- Screen enter/exit and tab switches use the pack's specs. With `Material`, transitions stay as they
  are today.

### Tests

- Unit: pack resolution (default pack equals today's theme values, reduced motion collapses specs).
- Screenshot or instrumented checks that showcase renders identically before and after (anti-goal).
- An instrumented test that a shared-element key on two destinations animates without crashing, and
  that the helpers no-op outside the shell.

## 4. Reminders (`:core:reminders`)

### API

```kotlin
interface Reminders {
    suspend fun schedule(reminder: Reminder)          // replaces any reminder with the same id
    suspend fun cancel(id: String)
    suspend fun cancelAll()
    val permission: StateFlow<NotificationPermission> // Granted, NotAskedYet, Denied, BlockedInSettings
}
data class Reminder(
    val id: String,              // app-chosen, stable (e.g. "netflix:renewal")
    val at: LocalDateTime,       // in the device's time zone at fire time
    val channel: String,         // a channel the app declared
    val title: String, val text: String,
    val deepLink: String?,       // opened through the app's deep-link scheme
)
```

- **Scheduling:** `AlarmManager.setAndAllowWhileIdle`, one alarm per reminder. The schedule is
  persisted in a `JsonStore`, so a `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` or app-update
  receiver can re-register every pending reminder. A reminder whose time passed while the phone was
  off fires once on boot if it's less than 12 hours late, otherwise it's dropped.
- **Channels:** an app declares them in its definition (`reminders { channel("renewals", R.string.…) }`).
  The module creates them at startup.
- **Permission:** Android 13+ `POST_NOTIFICATIONS`. The module tracks the state and offers
  `requestPermission()` for the system dialog and `openNotificationSettings()` for the blocked state.
  The rationale screen is the app's (Munch shows it after the first add); the module never prompts on
  its own.
- **Tapping a notification** opens its `deepLink` through the shell's existing deep-link handling.
- **Content is the app's.** The module doesn't group, throttle or reword. (Munch's "one per creature
  per day" rule lives in Munch.)

### Tests

- Unit: schedule persistence, replacement by id, boot rescheduling decisions (late < 12 h fires, older
  drops) with a fake clock and a fake alarm scheduler.
- Instrumented: a reminder scheduled a minute ahead posts a notification; the receiver re-registers
  after a simulated boot broadcast.

## 5. Firebase (`:core:firebase`)

### Opt-in

- `app.properties`: `firebase.enabled=true`. The application plugin then applies the
  `google-services` and `crashlytics` Gradle plugins and **fails the build** if the app module has
  no `google-services.json`. Apps without the key are untouched (showcase, globex).
- Debug builds: Analytics and Crashlytics collection are **off** (so test sessions don't pollute the
  market-test numbers), Remote Config fetches with a short minimum interval.

### What it provides

- **`RemoteValues`**, a new interface in `:core:domain`:
  `long(key, default)`, `double(key, default)`, `string(key, default)`, `boolean(key, default)`,
  `json(key, default)`, and a `changes: Flow<Unit>`. Defaults always come from the app's code, so the
  app works offline and before the first fetch. `:core:firebase` implements it with Remote Config
  (fetch-and-activate on start, cached activation on later launches). Without Firebase, the shell
  binds a defaults-only implementation, so features can depend on `RemoteValues` unconditionally.
- **`Analytics` and `CrashReporter`** implementations of the existing `:core:analytics` interfaces,
  passed through the existing DSL (`analytics(...)`), so the design spec's "no duplicate bindings"
  rule holds. A `firebase()` DSL call wires all three.
- Feature flags from `RemoteValues` are **not** merged with the backend's `app/config` flags in this
  work. A standalone app without a backend uses `RemoteValues` directly; tabs gated by flags keep
  using the existing mechanism.

### Tests

- Unit: the defaults-only implementation; typed reads with missing/garbled values fall back to the
  default (garbled JSON never crashes).
- The Firebase implementation is thin and covered by a manual check in the fixture app.

## 6. Ads (`:core:ads`)

### Opt-in and config

- `app.properties`: `ads.admob.appId` (manifest meta-data via placeholder) and one key per placement,
  e.g. `ads.banner.home`. Validation: present and well-formed when ads are used. **Debug builds always
  use Google's test IDs**, whatever the properties say, so a developer can't click their own live ads.
- DSL: `ads { placement("home") }`. A placement not declared both in the DSL and in properties fails
  the build or startup with a clear message.

### What it provides

- `AdBanner(placement: String, modifier)`, a composable that shows an adaptive banner, labelled "Ad",
  or **collapses to zero height** when it shouldn't show or no ad loads. It never shows an error.
- **House rules enforced inside the module**, not left to apps:
  - No ad request before consent is resolved (Google UMP). The consent form appears the first time an
    ad would load, never at startup.
  - No ads on the **first launch** (counted by the module).
  - No ads while an **ad-free window** is active.
  - Only banner and (later) native formats exist in the API; there is no interstitial to misuse.
- **Ad-free window:** `AdFree.grantUntil(instant)`, `AdFree.extend(by: Duration)`, and
  `AdFree.state: StateFlow<AdFreeState>` (active until / inactive). Persisted, so it survives restarts.
  It uses the wall clock; a user who sets the clock forward only shortens their own window. How time
  is earned (Munch's points) is the app's business.
- `Consent.showPrivacyOptions(activity)` for the app's Settings ("Ad privacy choices"), and
  `Consent.isPrivacyOptionsRequired` to hide the row where the law doesn't require it.
- Native ads are **not** in this work. The API leaves room (`AdSlot.Native` later).

### Tests

- Unit: the gate logic (consent × first launch × ad-free window × placement) as a pure function.
- Ad-free persistence and expiry with a fake clock.
- Instrumented: in the fixture app with test IDs, the banner collapses on first launch and appears on
  the second.

## 7. Roadmap (branches in this repo)

| # | Branch | Deliverable |
|---|---|---|
| 1 | `feat/standalone-apps` | `gradle/foundation.settings.gradle.kts`, optional `api.*` keys (no-backend apps), backup rules, APK-path mode for `verify-apps.sh`, `fixtures/standalone-app` + CI smoke build, `docs/creating-a-standalone-app.md` |
| 2 | `feat/style-packs` | `StylePack` (Material, Gallery), components read it, `style(…)` DSL, shell `SharedTransitionLayout` + helpers, haptics helper, docs in `branding.md` |
| 3 | `feat/reminders` | `:core:reminders` as above, docs |
| 4 | `feat/firebase` | `RemoteValues` in `:core:domain`, defaults-only binding in the shell, `:core:firebase`, `firebase.enabled` plugin support, docs |
| 5 | `feat/ads` | `:core:ads` as above, `ads.*` properties validation, docs |

Each ends with the usual checkpoint: `scripts/check.sh` green, every in-repo app and the fixture
build, docs updated, a PR, and the owner merges. Munch's own work (its repo, design and app spec)
starts after #1 and builds on each piece as it lands.

## 8. Left to the builder

- Exact class and file names beyond the public API above; the internal shape of `StylePack`.
- Whether the type scale is expressed as an M3 `Typography` transform or a separate table.
- The spring and easing values for `Material` (must match today's behavior) and the exact
  translation of the Gallery motion table into Compose specs.
- How `foundation.settings.gradle.kts` lists modules (hand-written list vs. directory scan), as long
  as adding a shared module to the foundation needs one edit in one place.
- Notification small-icon resource name (an app-provided brand resource with a default, like the
  splash icon).
- Remote Config fetch timing details beyond "never blocks the first frame".
