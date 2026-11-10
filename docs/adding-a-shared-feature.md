# Adding a shared feature

A shared feature is a module that any app can add with one line in its definition, e.g.
`settings()`. It must work for every app, configured only through what the app passes in. App
identity comes from `BrandConfig`; behaviour comes from the feature's options. A feature only one
app needs goes under that app instead (`apps/<name>/<feature>`) and follows the same rules.
`apps/globex/notes` is the worked example.

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

data class SettingsOptions(val showNotifications: Boolean = true)

fun AppDefinitionBuilder.settings(options: SettingsOptions = SettingsOptions()) {
    add(
        FeatureEntry(
            id = "settings",
            flagKey = null,                                  // or a key, to allow switching it off remotely
            tab = Tab(SettingsRoute, R.string.settings_tab, Icons.Default.Settings),
            routes = { navigator -> composable<SettingsRoute> { SettingsRoot(options, navigator) } },
        ),
    )
}
```

- **Mark everything else `internal`.** Hilt modules and ViewModels may be internal too.
- **Configuration only through the options and DI:** inject `BrandConfig`, never read
  `BuildConfig`, and never compare application IDs or names.
- **Navigation:**
  - within the feature, use the routes it registers;
  - to go elsewhere, call `AppNavigator` (`toTab`, `navigate`, `back`, `openUrl`);
  - never import another feature's routes or depend on another feature's module.
- **Feature flags:** if the feature can be switched off remotely, give it a `flagKey`. Then document
  the key, which is also what apps list in `defaults.features`.
- **Strings:** every user-visible string goes in `strings.xml` with a French translation in
  `values-fr`.
- **Components:** use the design-system components (`AppScaffold`, `EmptyState`, `ErrorState`, …)
  so every app's brand applies.

## Data

- **Backend:** call it through `:core:network`'s helpers, with relative paths under the app's base
  URL (`"settings"`, not `"/api/v1/settings"`). The backend-starter contract is the same for every
  app; see [api-environments.md](api-environments.md).
- **Local storage:** use `JsonStore` from `:core:datastore`, with one file per feature (e.g.
  `context.jsonStoreFile("settings")`).
- **State shared between features** (current user, session) lives in `:core:domain` / `:core:data`
  (Phase 4), never in one feature that another feature reads.

## Tests (write them first)

- **ViewModel tests** use a fake of each interface: State and Action transitions, blank-input
  rules.
- **Data tests:** use `MockEngine` for network data sources and `@TempDir` for `JsonStore`-backed
  stores.
- **One Compose UI test** for the main path of the screen.
- **Add the feature to `apps/showcase`,** the reference app that uses every shared feature, and
  check it in `diagnostics` or by hand.

## Checklist

1. Module created under `feature/<name>` and included in `settings.gradle.kts`.
2. One public DSL function plus an options type; everything else `internal`.
3. No dependency on another feature or on any `apps/*` module.
4. Strings in English and French; design-system components used.
5. Tests: ViewModel, data, one UI test. `scripts/check.sh` green.
6. Showcase's definition calls it; the docs list its options and flag key.
