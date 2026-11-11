# Adding a shared feature

A shared feature is a module that any app can add with one line in its definition, e.g.
`settings()`. It must work for every app, configured only through what the app passes in. App
identity comes from `BrandConfig`; behaviour comes from the feature's options. A feature only one
app needs goes under that app instead (`apps/<name>/<feature>`) and follows the same rules.

## Module layout

```
feature/<name>/                         :feature:<name>
  build.gradle.kts                      whitelabel.android.library + .compose + .hilt (+ serialization)
  src/main/kotlin/com/whitelabel/feature/<name>/
    <Name>.kt                           public: the DSL function, options, routes others must name
    presentation/                       internal: Root/Screen composables, State, Action, ViewModel
    domain/                             internal: models, repository/data-source interfaces
    data/                               internal: DTOs, mappers, Ktor/JsonStore implementations, Hilt module
  src/main/res/values{,-fr}/strings.xml
  src/test/…                            ViewModel, repository and mapper tests with fakes
  src/androidTest/…                     Compose UI tests of the screen
```

List `whitelabel.android.library` before `whitelabel.hilt` in `plugins {}`. The Hilt plugin picks
its Android or JVM setup when it is applied.

## The public API is one function

```kotlin
// <Name>.kt
@Serializable data object SettingsRoute                     // public only if an app must name it

data class SettingsOptions(val showNotifications: Boolean = true, val languages: List<String> = listOf("en", "fr"))

fun AppDefinitionBuilder.settings(options: SettingsOptions = SettingsOptions()) {
    add(
        FeatureEntry(
            id = "settings",
            flagKey = null,                                  // or a key, to allow switching it off remotely
            tab = Tab(SettingsRoute, R.string.settings_tab, Icons.Default.Settings),
            requiresSignIn = false,                          // true if it calls endpoints that need a session
            routes = { scope -> screen<SettingsRoute> { SettingsRoot(options, scope.navigator) } },
        ),
    )
}
```

- **Mark everything else `internal`.** Hilt modules and ViewModels may be internal too.
- **Configuration only through the options and DI:** inject `BrandConfig`, never read
  `BuildConfig`, and never compare application IDs or names.
- **Navigation:** `routes` receives a `FeatureScope`.
  - within the feature, use the routes it registers;
  - to go elsewhere, call `scope.navigator` (`toTab`, `canOpenTab`, `navigate`, `back`, `openUrl`);
  - to accept deep links, build them from `scope.deepLinkScheme`
    (`navDeepLink<DetailRoute>(basePath = "${scope.deepLinkScheme}://detail")`), never from a
    literal scheme;
  - never import another feature's routes or depend on another feature's module.
- **Sign-in:** set `requiresSignIn = true` when the feature calls endpoints that need a session.
  An app that adds the feature without `auth()` then fails at startup with a message naming
  `auth()`. Read the user and session from `:core:domain` (`UserRepository`, `SessionRepository`).
- **Analytics:** inject `Analytics` (`:core:analytics`) and use the names in `AnalyticsEvents`.
  Event params never contain an email, a name, a token or text the user typed. The shell already
  tracks `screen_view`.
- **Feature flags:** if the feature can be switched off remotely, give it a `flagKey`. Then document
  the key, which is also what apps list in `defaults.features`.
- **Strings:** every user-visible string goes in `strings.xml` with a French translation in
  `values-fr`.
- **Components:** use the design-system components (`AppScaffold`, `EmptyState`, `ErrorState`, …)
  so every app's brand applies.
- **Destinations and motion:** register destinations with `screen<Route> { }` (from the shell)
  rather than `composable<Route>`, so shared elements work inside them:
  - mark an element that moves to the next screen with `Modifier.sharedElementTransition(key)`
    (same content, e.g. an avatar) or a container that morphs with `Modifier.sharedBoundsTransition(key)`,
    using the same `key` on both screens. Both do nothing outside the shell;
  - time in-screen animations with `AppTheme.style.motion` (`emphasis`, `snackbar`, …) and
    `toAnimationSpec()`, never with hard-coded durations, and skip ambient animations when
    `AppTheme.reducedMotion` is true;
  - play feedback with `val haptics = rememberHaptics()` then `haptics(HapticEvent.Confirm)`; the
    app's pack decides whether anything plays.

## Data

- **Backend:** call it through `:core:network`'s helpers, with relative paths under the app's base
  URL (`"settings"`, not `"/api/v1/settings"`). The backend-starter contract is the same for every
  app; see [api-environments.md](api-environments.md). Signed-in calls carry the bearer and refresh
  on their own; use `postPublicResult` for calls that must not (sign-in, password reset).
- **Errors:** show `DataError.toUiText()` from `:core:ui`, after mapping the codes your screen
  handles specially (for example `INVALID_CREDENTIALS`).
- **Local storage:** use `JsonStore` from `:core:datastore`, with one file per feature (e.g.
  `context.jsonStoreFile("settings")`).
- **State shared between features** (current user, session, app config) lives in `:core:domain`
  and `:core:data`, never in one feature that another feature reads.

## Tests (write them first)

- **ViewModel tests** use a fake of each interface: State and Action transitions, blank-input
  rules.
- **Data tests:** use `MockEngine` for network data sources and `@TempDir` for `JsonStore`-backed
  stores.
- **Contract test:** a `ContractTest` that runs the module's data source against `ContractBackend`
  (`:core:testing`), so it decodes the spec's examples and sends bodies the spec accepts. See
  [api-integration.md](api-integration.md#contract-tests).
- **One Compose UI test** for the main path of the screen.
- **Add the feature to `apps/showcase`,** the reference app that uses every shared feature, and
  check it by hand.

## Checklist

1. Module created under `feature/<name>` and included in `settings.gradle.kts`.
2. One public DSL function plus an options type; everything else `internal`.
3. No dependency on another feature or on any `apps/*` module.
4. Strings in English and French; design-system components used.
5. Tests: ViewModel, data, one UI test. `scripts/check.sh` green.
6. Showcase's definition calls it; the docs list its options and flag key.
