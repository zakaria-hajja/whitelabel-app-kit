# Creating a new app

This repo is a foundation for many independent Android apps. A new app is a small module under
`apps/<name>/`. It holds the app's configuration, its brand resources, and one file that lists the
features it uses. The shared modules (`core/*`, and later `feature/*`) provide the rest: the launcher
activity, splash, theme, navigation, networking, storage and the design system.

The worked example is `apps/globex`. It is Globex's own app: sign-in, a local notes feature and
the shared settings. Creating it touched only `apps/globex/**` and `settings.gradle.kts`.

## 0. Before you start

- **Pick a short, lowercase name** for the module, e.g. `globex`. It becomes the Gradle path
  `:apps:globex` and the folder `apps/globex/`.
- **Know the app's backend.** Every app has its own backend, built from the backend starter
  (`../backend-api`):
  - **Shared-mode backend** (several tenants on one deployment): the tenant must exist. In the
    backend repo, run `npm run tenant -- create --slug globex --name "Globex"`. You'll set
    `api.tenant=globex`.
  - **Dedicated backend** (one tenant per deployment): leave `api.tenant` out. No `X-Client-Id`
    header is sent.

## 1. Create the folder

```
apps/globex/
  app.properties
  build.gradle.kts
  proguard-rules.pro       (app-specific R8 rules; the build always reads this file)
  lint.xml                 (lint settings for brand resources)
  src/main/AndroidManifest.xml
  src/main/kotlin/com/whitelabel/apps/globex/GlobexApp.kt
  src/main/kotlin/com/whitelabel/apps/globex/GlobexDefinition.kt
  src/main/res/            (icons, splash, brand colors; see step 3)
  src/test/kotlin/com/whitelabel/apps/globex/GlobexIdentityTest.kt
```

The quickest start is to copy `apps/showcase`, without its `build/` folder, then rename the
Kotlin package, the classes and every mention of Showcase:

```bash
# Never copy another app's signing material: each app signs with its own key.
rsync -a --exclude build --exclude signing.properties --exclude keys \
  --exclude '*.jks' --exclude '*.keystore' --exclude '*.p12' apps/showcase/ apps/globex/
cd apps/globex
for d in src/main/kotlin src/test/kotlin; do mv $d/com/whitelabel/apps/showcase $d/com/whitelabel/apps/globex; done
for f in $(find src -name 'Showcase*.kt'); do mv "$f" "$(echo "$f" | sed 's/Showcase/Globex/')"; done
# Text files only (GNU sed: -i without '')
grep -rl -i showcase --include='*.kt' --include='*.kts' --include='*.xml' --include='*.properties' . \
  | xargs sed -i '' -e 's/Showcase/Globex/g' -e 's/showcase/globex/g'
```

Then go through steps 2 to 7. The copied values are placeholders until you replace them.

## 2. `app.properties`: identity and API

The build validates this file and reports every problem at once, naming the file and key.

| Key | Example | Rule |
|---|---|---|
| `app.applicationId` | `com.whitelabel.globex` | Java package syntax; unique across apps |
| `app.name` | `Globex` | Launcher label. Any text; quotes and apostrophes are fine. No control characters |
| `app.versionCode` | `1` | Integer, 1..2100000000 |
| `app.versionName` | `1.0.0` | Non-blank |
| `app.deepLinkScheme` | `globex` | `^[a-z][a-z0-9+.-]*$`; unique across apps |
| `api.debug.baseUrl` | `http://10.0.2.2:3000/api/v1/` | Optional, together with `api.release.baseUrl`. `http` only to `10.0.2.2` or `localhost`; otherwise `https` |
| `api.release.baseUrl` | `https://api.globex.example/api/v1/` | Optional, together with `api.debug.baseUrl`. `https`, and not a local host |
| `api.tenant` | `globex` | Optional. The backend tenant slug, `^[a-z0-9][a-z0-9-]{1,62}$`. Needs the base URLs |
| `defaults.support.email` | `support@globex.example` | Optional |
| `defaults.legal.privacyPolicyUrl` | `https://globex.example/privacy` | `https` |
| `defaults.legal.termsUrl` | `https://globex.example/terms` | `https` |
| `defaults.features` | `catalog,profile` | Optional. Flagged features that are on when remote config is unavailable |

- **Base URLs** are the versioned API root. A missing trailing `/` is added for you.
- **No backend:** leave out both base URLs (and `api.tenant`) for a local-only app. The shell then
  skips the startup config fetch, and the app refuses to start if its definition includes a feature
  that signs in or calls the API (`auth()`, or any entry with `requiresBackend`). Setting only one
  base URL is a build error.
- **Environment:** debug builds are `DEVELOPMENT` (with an `.debug` application ID suffix) and
  release builds are `PRODUCTION`.
- **More detail:** see [api-environments.md](api-environments.md).

## 3. Icon, splash and colors (`src/main/res`)

**Required** (the build fails without them):

| File | What it is |
|---|---|
| `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | Adaptive launcher icon: background color, foreground and monochrome drawables |
| `drawable/ic_launcher_foreground.xml`, `drawable/ic_launcher_monochrome.xml` | The glyph, inside the 66dp safe zone of the 108dp canvas |
| `drawable/brand_splash_icon.xml` | Splash-screen icon |
| `values/*.xml` declaring `<color name="brand_splash_background">` and `<color name="ic_launcher_background">` | Splash and icon background |

**Optional:** any `brand_light_*`/`brand_dark_*` color role, the corner radii and the fonts. Anything you
leave out uses the design-system default. [branding.md](branding.md) lists every name and the
contrast rule. Android Studio's *Image Asset* tool can produce the icon files; export them into
`src/main/res`.

## 4. The two Kotlin files

```kotlin
// GlobexApp.kt: Hilt needs the Application class in the app module.
@HiltAndroidApp
class GlobexApp : FoundationApplication()
```

```kotlin
// GlobexDefinition.kt: everything the app contains, in one readable list.
@Module
@InstallIn(SingletonComponent::class)
object GlobexDefinition {
    @Provides
    @Singleton
    fun definition(): AppDefinition = appDefinition {
        auth()                  // shared: sign-in gates the whole app
        notes()                 // app-specific feature (step 6)
        settings()              // shared: theme, language, notifications, about, sign out
        startTab(NotesRoute)    // optional; defaults to the first tab
    }
}
```

- **Features:** each one you add is a single call, and the order is the order of the bottom-bar
  tabs.
- **Diagnostics:** debug builds get a diagnostics screen (identity, backend ping, design-system
  catalog) under Settings › About. It is not in release builds, and you don't add it. The shared features, their options and flag keys are listed in
  [shared-features.md](shared-features.md).
- **Sign-in:** `auth()` makes the whole app sign-in only. Features that need a session (profile,
  home, catalog) refuse to start without it, with a message naming `auth()`. Without `auth()` the
  app is public.
- **Tabs and the bottom bar:** an app needs at least one tab. The bottom bar appears when two or
  more tabs are visible.
- **Remote flags:** a feature with a flag key can be switched off remotely. When the remote config
  is unavailable, it falls back to `defaults.features`.

## 5. Build file and manifest

```kotlin
// apps/globex/build.gradle.kts
plugins {
    id("whitelabel.android.application")
}

android {
    namespace = "com.whitelabel.apps.globex"
}

dependencies {
    implementation(project(":core:shell"))
    implementation(project(":feature:authentication"))   // one line per shared feature used
    implementation(project(":feature:settings"))
    implementation(project(":apps:globex:notes"))        // app-specific feature modules
}
```

```xml
<!-- src/main/AndroidManifest.xml: everything else comes from :core:shell. -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:name=".GlobexApp" />
</manifest>
```

Add the module, and any app-specific feature modules, to `settings.gradle.kts`:

```kotlin
include(":apps:globex")
include(":apps:globex:notes")
```

## 6. App-specific features

Put a feature that only this app needs in its own module under the app, e.g.
`apps/globex/notes/` (`:apps:globex:notes`). It may depend on any shared module, and never on
another app.

- **Public API:** the module exposes one DSL function (`fun AppDefinitionBuilder.notes()`), plus the
  route type if the definition must name it.
- **Everything else is `internal`:** screens, ViewModel and storage.
- **The pattern to copy:** see [adding-a-shared-feature.md](adding-a-shared-feature.md). An
  app-specific feature follows the same rules.

Globex's notes feature uses the shared JSON store from `:core:datastore`, the design-system
components and the shell's navigation. None of those modules know it exists.

## 7. Identity test

Copy `GlobexIdentityTest.kt` and put your values in it. It asserts every `BuildConfig` field for
both build types, so a wrong URL or ID fails `scripts/check.sh` and CI.

## 8. End-to-end tests

Put them in `src/androidTest/kotlin/com/whitelabel/apps/<name>/e2e/`. The app plugin already runs
them in a Hilt test app, against `:core:mockapi`'s in-memory backend, so they need no server.
`apps/showcase/.../e2e/ShowcaseE2ETest.kt` is the example to copy.

- **Rules:** use `RuleChain.outerRule(ResetAppDataRule()).around(hilt).around(EndAppScopeRule()).around(compose)`,
  with `createEmptyComposeRule()` and `ActivityScenario.launch(FoundationActivity::class.java)`.
  Each test then starts signed out, with fresh storage and a fresh backend.
- **Robots:** the shared screens already have robots in `com.whitelabel.core.testing.android.robots`.
  `SignInRobot(compose, appName = "<app.name>")` covers sign-in and registration,
  and `SettingsRobot` covers settings. Write a robot (`e2e/robots/`) only for the app's own screens.
- **Backend:** `@Inject lateinit var backend: FakeBackend` (call `hilt.inject()` first). Set it up,
  e.g. `backend.features["catalog"] = false`, before you launch; remote flags are read at start.
  Its seeded users are `alice@shared.test` and `bob@shared.test`, password `FakeData.PASSWORD`.
- **App-specific stores:** create a `JsonStore` in `@ApplicationScope` (from `:core:data`), never in a
  scope of its own. Otherwise its file stays open, and the second test fails with "multiple
  DataStores active for the same file".

Run them on a running emulator with `./gradlew :apps:<name>:connectedDebugAndroidTest`.

## 9. Build, verify, release

```bash
./gradlew :apps:globex:assembleDebug             # debug APK
./gradlew :apps:globex:testDebugUnitTest         # identity + feature tests
./gradlew :apps:globex:installDebug              # on a running emulator
scripts/verify-apps.sh --config-only             # IDs and schemes unique across apps
./gradlew assembleDebug assembleRelease && scripts/verify-apps.sh   # full APK checks
```

For a signed release, add `apps/globex/signing.properties` (gitignored); see
[release-signing.md](release-signing.md). Then run `./gradlew :apps:globex:bundleRelease` for the
Play Store AAB.

## What you must not touch

- Anything in `core/`, `feature/` or `build-logic/`. If the new app seems to need a change there,
  it's a change to the foundation and gets its own PR.
- Another app's folder.
- Branching on app identity in shared code (`if (applicationId == …)`). Pass options through the
  feature's DSL call instead.

## Checklist

1. Backend tenant exists (shared mode) or a dedicated backend URL is known.
2. `apps/<name>/` copied from showcase without `build/`, package names renamed.
3. `app.properties` filled in; `scripts/verify-apps.sh --config-only` passes.
4. Icons, splash and colors replaced in `src/main/res`.
5. `<Name>App` and `<Name>Definition` written; the features listed.
6. `build.gradle.kts` namespace and dependencies set; the manifest names the App class.
7. Modules added to `settings.gradle.kts`.
8. Identity test updated; `./gradlew :apps:<name>:testDebugUnitTest` passes.
9. An `e2e/` test signs in and reaches the app's start screen; `connectedDebugAndroidTest` passes.
10. `scripts/check.sh` passes.
11. Both builds assemble and `scripts/verify-apps.sh` passes.
