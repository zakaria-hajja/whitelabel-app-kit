# Creating a standalone app (its own repo)

An app can live in its own repository and still build the foundation's modules from source. The
foundation is a **git submodule** in `foundation/`, pinned to one commit: that commit is the
foundation version the app uses. `fixtures/standalone-app/` in this repo is a working example (it
points at `../..` instead of `foundation/`), and CI builds it on every pull request.

For everything inside the app module (`app.properties`, brand resources, the app definition, an
app-specific feature), [creating-an-app.md](creating-an-app.md) applies unchanged. This page covers
what differs.

## 1. Repo layout

```
myapp/
  foundation/              git submodule → this repo
  app/                     the app module (applies whitelabel.android.application)
    app.properties
    build.gradle.kts
    proguard-rules.pro
    src/main/…
  feature/…                the app's own feature modules (optional, any names)
  build.gradle.kts         plugin declarations (copied, see step 3)
  settings.gradle.kts
  gradle.properties
  gradlew, gradle/wrapper/ (copy the foundation's wrapper)
```

## 2. Add the foundation

```sh
git init -b main myapp && cd myapp
git submodule add https://github.com/zakaria-hajja/android-starter.git foundation
mkdir -p gradle && cp foundation/gradlew . && cp -R foundation/gradle/wrapper gradle/
cp foundation/gradle.properties .
```

`local.properties` (with `sdk.dir`) stays uncommitted, as in the foundation.

`gradle.properties` includes `kotlin.native.ignoreDisabledTargets=true`. The foundation's shared
modules are Kotlin Multiplatform, and this keeps a Linux CI from warning about the iOS targets it
can't build. When the foundation adds a property, copy the file again.

On a Mac without Xcode, the KMP modules skip their iOS simulator tests with a warning, and iOS code
still compiles. `-Pwhitelabel.skipIosTests=true` skips them anywhere.

## 3. Settings and root build file

`settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("foundation/build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "myapp"

// Every shared module (:core:shell, :core:designsystem, …) under its usual path, plus the
// foundation's version catalog as `libs`.
extra["foundationDir"] = settingsDir.resolve("foundation")
apply(from = "foundation/gradle/foundation.settings.gradle.kts")

include(":app")
```

`build.gradle.kts`: copy `foundation/build.gradle.kts` as it is. It declares the plugins (without
applying them) so the convention plugins can apply them by id. A `plugins {}` block can't come
from an applied script, so this one file is a copy; when the foundation adds a plugin, add it here
too (the build fails with "Plugin with id … not found" until you do).

**Version catalog:** the app uses the foundation's (`libs`). A library only this app needs is
declared directly in its module, e.g. `implementation("group:name:1.2.3")`. When a second app
needs it, move it into the foundation's catalog.

## 4. Apps without a backend

Leave `api.debug.baseUrl`, `api.release.baseUrl` and `api.tenant` out of `app.properties`. Then:

- the shell doesn't fetch the remote app config; runtime config is the app's build-time defaults;
- diagnostics (Settings › About, debug builds) shows "No backend" and hides the ping;
- `settings()` works without a backend: theme and language are local, and the account and
  notification parts stay hidden because nobody signs in;
- the app refuses to start if its definition includes `auth()` or any feature that calls the API,
  with a message naming the feature.

Local notifications (renewal reminders and the like) need no backend either: see
[reminders.md](reminders.md).

## 5. End-to-end tests

They work the same as in the foundation's own apps, because the app plugin wires
`:core:testing-android` (with `FreshAppRule`) and `:core:mockapi` into `androidTest`. See
[creating-an-app.md, section 8](creating-an-app.md#8-end-to-end-tests) for the rules, the shared
robots and the in-memory backend. A backendless app still gets the clean-start rules. It only
needs the fake if it calls the API.

## 6. Backups

The shell backs up the app's `JsonStore` files (`filesDir/datastore/`) through Android backup, so
a reinstall or a new phone restores the user's data when they have Google backup on. The session
(`session.json`) and the signed-in user (`user.json`) are never backed up: the tokens are encrypted
with a Keystore key that can't be restored, so a restored phone starts signed out. Store app data
with `JsonStore` under `jsonStoreFile(name)` to get this for free.

## 7. CI

The app repo gets the same rules and checks as the foundation (Conventional Commits, `.githooks/`,
a `scripts/check.sh`, the `ci` job). Check out the app repo with the job's own token, fetch the
private foundation with a secret used for that one command, then build and verify the APK. This is
the setup Munch runs:

```yaml
      # FOUNDATION_READ_TOKEN: a fine-grained token with Contents: read on the foundation repo only.
      - name: Require FOUNDATION_READ_TOKEN
        env:
          T: ${{ secrets.FOUNDATION_READ_TOKEN }}
        run: |
          if [ -z "$T" ]; then
            echo "::error::Secret FOUNDATION_READ_TOKEN is not set. Add a fine-grained token with read access to the foundation repo (repo Settings > Secrets > Actions)."
            exit 1
          fi
      - name: Checkout
        uses: actions/checkout@v7
        with:
          fetch-depth: 0
          persist-credentials: false      # nothing later needs the job token
      - name: Fetch the foundation submodule
        env:
          T: ${{ secrets.FOUNDATION_READ_TOKEN }}
        run: |
          git -c "http.https://github.com/zakaria-hajja/android-starter.git/.extraheader=AUTHORIZATION: basic $(printf 'x-access-token:%s' "$T" | base64 -w0)" \
            submodule update --init foundation
      # setup-java (17) and setup-gradle as in the foundation's ci.yml
      - run: scripts/check.sh
      - run: ./gradlew :app:assembleDebug :app:assembleRelease
      - run: |
          foundation/scripts/verify-apps.sh --apk app/build/outputs/apk/debug/app-debug.apk \
            --properties app/app.properties --type debug
```

`verify-apps.sh --apk` checks package, label, version, deep-link scheme and the generated config
resources against `app.properties`. The checks that compare apps with each other only run inside
the foundation.

The secret is used only on the submodule command, scoped to the foundation's URL, so it never
lands in the saved git config. A token that can't read the foundation fails that step with
`403 … Write access to repository not granted` (GitHub's wording for "no access").

## 8. Working on the foundation from an app

1. Change the foundation inside `foundation/` on a branch of the foundation repo, and land it through
   the foundation's own PR (with its CI and `scripts/check.sh`).
2. Once it's pushed (merged, or at least pushed for a work-in-progress app branch), move the
   pointer in the app repo: `git -C foundation checkout <commit>`, then commit
   `chore(deps): bump foundation to <short sha>`.
3. Never commit a pointer to a commit that only exists on your machine: CI and everyone else would
   fail to check it out.

To update later: `git -C foundation fetch && git -C foundation checkout origin/main`, build, run the
app's checks, commit the bump.

## 9. Moving to a foundation without Hilt

The foundation replaced Hilt with Koin. An app repo that bumps `foundation/` past that change
updates the following, in one commit with the bump:

1. **Root build file:** copy `foundation/build.gradle.kts` again. The `hilt` and `ksp` plugin
   aliases are gone from the catalog, so an old copy no longer resolves. Copy
   `foundation/gradle.properties` too.
2. **Module build files:** replace `id("whitelabel.hilt")` with `id("whitelabel.koin")`, and drop
   `libs.androidx.hilt.navigation.compose`.
3. **Application class:** drop `@HiltAndroidApp` and override `definition()`:
   `class MyApp : FoundationApplication() { override fun definition() = myDefinition() }`.
4. **Definition:** the `@Module @InstallIn object … { @Provides fun definition() }` becomes a plain
   `fun myDefinition(): AppDefinition = appDefinition { … }`.
5. **The app's own bindings:** each Hilt module becomes a Koin `module { … }`, added with
   `modules(...)` in the definition. Features load first and the app's modules after them, so an
   app binding replaces a shared one of the same type. A `@Provides ReminderConfig` becomes
   `modules(remindersModule(ReminderConfig(...)))`. Work that must outlive a screen uses
   `get<CoroutineScope>(ApplicationScope)`.
6. **ViewModels and screens:** drop `@HiltViewModel` and `@Inject`, declare each with
   `viewModelOf(::MyViewModel)` in the app's module, and get it with `koinViewModel()`
   (`org.koin.androidx.compose`).
7. **Tests:**
   - Replace `HiltAndroidRule`, `ResetAppDataRule` and `EndAppScopeRule` with
     `RuleChain.outerRule(FreshAppRule()).around(compose)`.
   - Replace `@Inject lateinit var backend: FakeBackend` with a getter,
     `GlobalContext.get().get<FakeBackend>()`.
   - `FoundationTestRunner` is gone. The app plugin sets AndroidX's runner.
   - Add a device test calling `GlobalContext.get().assertEveryViewModelResolves()` (from
     `:core:testing-android`) with a `FreshAppRule`. Koin finds a missing binding only when a screen
     opens; this test finds it first.

## 10. Moving to a foundation with KMP core modules

The core modules (`common`, `domain`, `data`, `datastore`, `network`, `analytics`, `branding`,
the reminder engine) are now Kotlin Multiplatform. An Android app repo changes little:

- **`Reminder.at`** is a `kotlinx.datetime.LocalDateTime`. From `java.time`, use
  `toKotlinLocalDateTime()`; the stored format is unchanged.
- **`JsonStore`** takes an okio `Path` (`context.jsonStorePath(name)`). The `File` forms
  (`JsonStore(file, …)` and `context.jsonStoreFile(name)`) still work on Android.
- **`StorePaths`** returns a `Path` (`path(name)`) instead of a `File` (`file(name)`).
