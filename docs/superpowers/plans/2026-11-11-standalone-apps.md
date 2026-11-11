# Standalone Apps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an app live in its own repo with the foundation as a git submodule, including apps with no backend, and prove it in CI with a fixture app.

**Architecture:** `api.*` keys become optional end to end (build-logic → generated resources → `BrandConfig` → shell startup). One settings script, `gradle/foundation.settings.gradle.kts`, lists every shared module and is applied both by the foundation's own settings and by an app repo's settings. A minimal app under `fixtures/standalone-app/` builds through that script in CI and is checked by a new single-APK mode of `scripts/verify-apps.sh`. The shell's backup rules switch from "nothing" to "JsonStore files minus session data".

**Tech Stack:** Gradle Kotlin DSL settings scripts, AGP convention plugins (build-logic), Kotlin, Hilt, JUnit 5 + AssertK, POSIX sh, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-10-standalone-app-support-design.md` (§2, §7 row 1).

**Deviations from the spec (decided while planning):** the backend check runs at app startup, not at build time, because the definition is runtime Kotlin; and the shell still binds an HTTP client for backendless apps, pointed at `https://no-backend.invalid/`, so nothing in the Hilt graph has to become optional.

**Branch:** `feat/standalone-apps`, created from `docs/standalone-app-support` (so the spec travels with it) in a worktree. Never work in the main checkout: it holds someone else's uncommitted work on `feat/settings`.

## Global Constraints

- Showcase and globex must build and behave exactly as before; `scripts/verify-apps.sh` (no args) must still pass for them.
- No app identity (name, application ID, URL, colors) in `core/*`, `feature/*`, `build-logic` or `gradle/`.
- Shared modules never depend on `apps/*` or `fixtures/*`.
- `scripts/check.sh` passes before every push; Conventional Commits; each commit ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Tests: JUnit 5 + AssertK, fakes over mocks, no I/O in unit tests except build-logic's existing `@TempDir` pattern and the backup-rules file read in Task 3.
- `.invalid` is the placeholder host for apps without a backend (RFC 2606: never resolves).

## Review Focus

- `app.properties` with only one of `api.debug.baseUrl` / `api.release.baseUrl`: the build fails naming both keys, not a half-configured app. (Task 1)
- `api.tenant` without base URLs: the build fails, since a tenant header with no backend is a config mistake. (Task 1)
- An app with no backend that includes `auth()` (or any backend feature): startup fails with a message naming the feature and the missing keys, not a network error later. (Task 2)
- A restore from backup brings back preferences and the app's own stores but never `session.json` or `user.json`, so a restored phone is signed out cleanly. (Task 3)
- Applying the settings script from the foundation root must not create a second `libs` catalog or change the project list. (Task 4)

---

### Task 1: Optional API keys in build-logic

**Files:**
- Modify: `build-logic/convention/src/main/kotlin/com/whitelabel/buildlogic/apps/AppConfig.kt`
- Modify: `build-logic/convention/src/main/kotlin/com/whitelabel/buildlogic/apps/AppConfigLoader.kt`
- Modify: `build-logic/convention/src/main/kotlin/com/whitelabel/buildlogic/apps/AppConfigFields.kt`
- Modify: `build-logic/convention/src/main/kotlin/com/whitelabel/buildlogic/AndroidApplicationConventionPlugin.kt`
- Test: `build-logic/convention/src/test/kotlin/com/whitelabel/buildlogic/apps/AppConfigLoaderTest.kt`, `AppConfigFieldsTest.kt`

**Interfaces:**
- Produces: `AppConfig.apiBaseUrls: Map<String, String>?` (`null` = no backend); `AppConfigLoader.load(appDir: File, label: String = "apps/${appDir.name}")`; `appConfigFields(...)["API_BASE_URL"] == ""` when there is no backend.

- [ ] **Step 1: Write the failing tests** in `AppConfigLoaderTest`:
  - `no api keys means no backend`: overrides `api.debug.baseUrl`, `api.release.baseUrl`, `api.tenant` to `null` → `load(...).apiBaseUrls` is `null`.
  - `only one base url is rejected`: `api.release.baseUrl` → `null` → problems contain `"apps/sample/app.properties: api.debug.baseUrl and api.release.baseUrl must be set together"`.
  - `tenant without a backend is rejected`: both URLs `null`, tenant kept → problems contain `"apps/sample/app.properties: api.tenant needs api.debug.baseUrl and api.release.baseUrl"`.
  - `label is used in messages`: `load(dir, label = "app")` with `app.name` blank → message starts with `"app/app.properties: app.name"`.
  In `AppConfigFieldsTest`: `api base url is blank without a backend` → `appConfigFields(configWithoutBackend, "debug")["API_BASE_URL"]` is `""`, for `"release"` too.
- [ ] **Step 2: Run** `./gradlew -p build-logic test` — Expected: the five new tests FAIL.
- [ ] **Step 3: Implement.** In the loader, read both URL keys with `value(...)` first: both blank → `apiBaseUrls = null` and `api.tenant` must be blank (else the problem above); exactly one → the "set together" problem; both → today's `url(...)` validation unchanged. Replace the hard-coded `"apps/${dir.name}"` label with the `label` parameter (also used in signing messages). `appConfigFields` uses `config.apiBaseUrls?.getValue(buildType).orEmpty()`. The plugin passes `label = rootDir.toPath().relativize(projectDir.toPath()).toString()` so standalone repos see `app/app.properties`.
- [ ] **Step 4: Run** `./gradlew -p build-logic test` — Expected: PASS (all old tests too).
- [ ] **Step 5: Commit** `feat(build): make the backend URLs optional for apps without a backend`

### Task 2: Backendless apps in the shell

**Files:**
- Modify: `core/branding/src/main/kotlin/com/whitelabel/core/branding/BrandConfig.kt`
- Modify: `core/shell/src/main/kotlin/com/whitelabel/core/shell/brand/BrandConfigFactory.kt`, `brand/ShellModule.kt`
- Modify: `core/shell/src/main/kotlin/com/whitelabel/core/shell/AppDefinition.kt`, `ShellViewModel.kt`
- Modify: `core/shell/src/main/kotlin/com/whitelabel/core/shell/diagnostics/BrandPreviewViewModel.kt`, `BrandPreviewScreen.kt`
- Test: `core/shell/src/test/kotlin/com/whitelabel/core/shell/brand/BrandConfigFactoryTest.kt`, `AppDefinitionTest.kt`, `ShellViewModelTest.kt`, `diagnostics/` tests

**Interfaces:**
- Consumes: blank `foundation_api_base_url` for backendless apps (Task 1).
- Produces: `BrandConfig.apiBaseUrl: String?` and `val BrandConfig.hasBackend: Boolean get() = apiBaseUrl != null`; `FeatureEntry(requiresBackend: Boolean = false, …)`; `internal fun AppDefinition.backendProblem(hasBackend: Boolean): String?`; `internal const val NO_BACKEND_BASE_URL = "https://no-backend.invalid/"` in `ShellModule.kt`.

- [ ] **Step 1: Write the failing tests:**
  - `BrandConfigFactoryTest`: blank API URL → `apiBaseUrl` is `null`, `hasBackend` false; blank **app name** → throws with the existing "No app configuration found…" message (the "configured" check moves from the URL to the app name).
  - `AppDefinitionTest`: `backendProblem(hasBackend = false)` is `null` for `appDefinition { diagnostics() }`; for a definition with a gate entry, a `requiresSignIn` entry or a `requiresBackend` entry it equals `"<ids, comma-separated> call the backend: set api.debug.baseUrl and api.release.baseUrl in app.properties"`; with `hasBackend = true` it is always `null`.
  - `ShellViewModelTest`: with a backendless brand, `appConfig.refresh()` is never called and `ready` becomes true without waiting `CONFIG_WAIT_MS`; with a backend, behavior unchanged (existing tests). With a backendless brand and a gated definition, constructing the ViewModel throws `IllegalStateException` with the `backendProblem` message.
  - Diagnostics: backendless → state has `showPing == false`.
- [ ] **Step 2: Run** `./gradlew :core:shell:testDebugUnitTest` — Expected: new tests FAIL.
- [ ] **Step 3: Implement.** `brandConfigFrom` maps a blank URL to `null` and calls `requireConfigured` on the app name. `ShellModule.networkConfig` uses `brand.apiBaseUrl ?: NO_BACKEND_BASE_URL` (the client stays constructible; nothing calls it). `FeatureEntry` gains `requiresBackend`; `backendProblem` collects entries with `gate != null || requiresSignIn || requiresBackend`. `ShellViewModel.init` first does `definition.backendProblem(brand.hasBackend)?.let(::error)`, then skips the refresh launch and the wait when `!brand.hasBackend`. Diagnostics hides the ping row when `!brand.hasBackend`. Update every `BrandConfig(...)` construction in tests/fakes for the nullable type.
- [ ] **Step 4: Run** `scripts/check.sh` — Expected: PASS for all modules.
- [ ] **Step 5: Commit** `feat(shell): run apps that have no backend`

### Task 3: Back up JsonStore data, never the session

**Files:**
- Modify: `core/shell/src/main/AndroidManifest.xml` (`android:allowBackup="true"`, add `android:fullBackupContent="@xml/backup_rules"`, update the comment)
- Modify: `core/shell/src/main/res/xml/data_extraction_rules.xml` (API 31+)
- Create: `core/shell/src/main/res/xml/backup_rules.xml` (API 26–30)
- Test: `core/shell/src/test/kotlin/com/whitelabel/core/shell/BackupRulesTest.kt`

**Interfaces:**
- Consumes: `jsonStoreFile(name)` → `filesDir/datastore/<name>.json` (`core/datastore`); session and user caches are `session` and `user` (`core/data/.../DataModule.kt`).

- [ ] **Step 1: Write the failing test** `BackupRulesTest` (reads both XML files relative to the module dir with `javax.xml.parsers`):
  - `cloud backup and device transfer include only datastore` → in `data_extraction_rules.xml`, both `<cloud-backup>` and `<device-transfer>` contain exactly one `include` (`domain="file" path="datastore/"`) and the excludes `datastore/session.json`, `datastore/user.json`.
  - `legacy backup rules match` → `backup_rules.xml` (`<full-backup-content>`) has the same include and excludes.
- [ ] **Step 2: Run** `./gradlew :core:shell:testDebugUnitTest --tests '*BackupRulesTest'` — Expected: FAIL.
- [ ] **Step 3: Write the two XML files and the manifest change.** Comment in the manifest: tokens are encrypted with a Keystore key that can't be restored, so session data never leaves the device.
- [ ] **Step 4: Run** the test again, then `./gradlew :apps:showcase:assembleDebug` — Expected: PASS, build succeeds (lint has no backup warnings).
- [ ] **Step 5: Commit** `feat(shell): back up app data except the session`

### Task 4: One settings script for the foundation's modules

**Files:**
- Create: `gradle/foundation.settings.gradle.kts`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Produces: a script applied with `apply(from = …)` from any settings file. It reads `settings.extra["foundationDir"]` (a `File`; default `settingsDir.resolve("foundation")`), includes every shared module (`:core:branding`, `:core:common`, `:core:network`, `:core:designsystem`, `:core:datastore`, `:core:shell`, `:core:ui`, `:core:analytics`, `:core:domain`, `:core:data`, `:feature:authentication`) with `project(path).projectDir = foundationDir.resolve(<path as dirs>)`, and — only when `foundationDir` is not `settingsDir` — creates the `libs` catalog from `foundationDir/gradle/libs.versions.toml`. The module list is written once, here.

- [ ] **Step 1: Record the baseline:** `./gradlew -q projects > /tmp/…/projects-before.txt` (scratch dir).
- [ ] **Step 2: Write the script** and change `settings.gradle.kts` to `extra["foundationDir"] = settingsDir`, `apply(from = "gradle/foundation.settings.gradle.kts")`, then its own `include(":apps:…")` lines. `pluginManagement` and `dependencyResolutionManagement.repositories` stay in `settings.gradle.kts` (Gradle requires `pluginManagement` first in the file). Header comment: adding a shared module means one line in the script.
- [ ] **Step 3: Verify:** `./gradlew -q projects` diffs empty against the baseline, and `scripts/check.sh` passes.
- [ ] **Step 4: Commit** `build: list the shared modules in one settings script`

### Task 5: Fixture standalone app, single-APK verification, CI

**Files:**
- Create: `fixtures/standalone-app/settings.gradle.kts`, `gradle.properties` (copy of the root file's five properties), `app/build.gradle.kts`, `app/app.properties`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/com/whitelabel/fixture/standalone/{FixtureApp.kt,FixtureDefinition.kt}`, `app/src/main/res/…` (the four required assets and `values/brand.xml`, copied from showcase)
- Modify: `scripts/verify-apps.sh`, `.github/workflows/ci.yml`, `.gitignore` (fixture `build/`, `.gradle/`)

**Interfaces:**
- Consumes: Task 1 (no `api.*` keys), Task 2 (backendless shell), Task 4 (settings script).
- Produces: `scripts/verify-apps.sh --apk <file> --properties <app.properties> --type <debug|release>`, running the per-APK checks (package, label, version, deep-link scheme, config resource set and values, launcher foreground present) and skipping the cross-app ones (uniqueness, dex isolation, distinct icons).

- [ ] **Step 1: Write the fixture.** `settings.gradle.kts`: `pluginManagement { includeBuild("../../build-logic"); repositories {…} }`, the same `dependencyResolutionManagement` repositories, `rootProject.name = "standalone-app-fixture"`, `extra["foundationDir"] = settingsDir.resolve("../..")`, `apply(from = "../../gradle/foundation.settings.gradle.kts")`, `include(":app")`. `app.properties`: `app.applicationId=com.whitelabel.fixture.standalone`, `app.name=Standalone Fixture`, `app.versionCode=1`, `app.versionName=1.0.0`, `app.deepLinkScheme=standalonefixture`, `defaults.legal.privacyPolicyUrl=https://fixture.example/privacy`, `defaults.legal.termsUrl=https://fixture.example/terms`, no `api.*`. Definition: `appDefinition { diagnostics() }`. Namespace `com.whitelabel.fixture.standalone`.
- [ ] **Step 2: Build it:** `./gradlew -p fixtures/standalone-app :app:assembleDebug :app:assembleRelease` — Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Add the single-APK mode** to `verify-apps.sh`: move the per-APK checks into a function `verify_apk LABEL PROPS APK TYPE` used by both modes. In it, the expected `foundation_api_base_url` value is `""` when the properties have no `api.<type>.baseUrl`. Update the usage comment.
- [ ] **Step 4: Verify both modes:** `scripts/verify-apps.sh --apk fixtures/standalone-app/app/build/outputs/apk/debug/app-debug.apk --properties fixtures/standalone-app/app/app.properties --type debug` → all `OK`, exit 0; `./gradlew assembleDebug assembleRelease && scripts/verify-apps.sh` → all `OK` for showcase and globex (unchanged behavior). Then edit the fixture's `app.name` locally to `Wrong` without rebuilding → the label check prints `FAIL` and exits 1; revert.
- [ ] **Step 5: CI:** after "Verify apps", add "Standalone fixture" running `./gradlew -p fixtures/standalone-app :app:assembleDebug` and the `--apk` check above.
- [ ] **Step 6: Commit** `ci: build a standalone app fixture against the foundation`

### Task 6: Docs

**Files:**
- Create: `docs/creating-a-standalone-app.md`
- Modify: `docs/creating-an-app.md` (the `api.*` rows: optional as a pair; `api.tenant` needs them), `docs/architecture.md` (modules table and a "Standalone apps" paragraph pointing to the new doc), `README.md` (one line in the quick start)

- [ ] **Step 1: Write `creating-a-standalone-app.md`** covering, with copy-pasteable snippets taken from the fixture: `git submodule add <foundation repo> foundation`; the app's `settings.gradle.kts` (as the fixture's, with `foundationDir = settingsDir.resolve("foundation")` and paths without `../../`); `gradle.properties`; the app module (same as `creating-an-app.md`, but under `app/`); apps without a backend (leave out `api.*`, what then fails at startup); backups (what is and isn't restored); CI (`git submodule update --init`, build, `verify-apps.sh --apk`); the submodule workflow (land foundation changes through the foundation's PRs first, then bump the pointer in a `chore(deps)` commit; never point at an unpushed commit); extra libraries (declared directly in the app module; move to the foundation catalog once a second app needs them).
- [ ] **Step 2: Verify** every command and file path in the doc against the fixture (run the fixture build command as written).
- [ ] **Step 3: Commit** `docs: explain how to build an app in its own repo`

---

## After the last task

Run `scripts/check.sh`, `./gradlew assembleDebug assembleRelease`, `scripts/verify-apps.sh`, and the fixture build + `--apk` check. Push `feat/standalone-apps` and open the PR from the template (the owner merges).
