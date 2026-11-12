# Android App Foundation

An internal foundation for building many independent Android apps (Kotlin, Jetpack Compose,
Material 3, Koin, Ktor). Shared modules provide the app host, navigation, networking, storage and
the design system, plus ready-to-use features: sign-in, home, catalog, profile and settings (see
[docs/shared-features.md](docs/shared-features.md)).
Each app is a thin module under `apps/<name>/` with its own identity, brand, backend URL and
business features.

One app lives here today: `apps/showcase`, the reference app (sign-in, home, catalog, profile and
settings). New apps are added the same way; see [docs/creating-an-app.md](docs/creating-an-app.md).

**iOS.** Every app also ships on iOS. Rules, data and ViewModels are shared Kotlin (KMP); the
screens are SwiftUI, from `ios/WhitelabelKit` (the shell, the design system and the same five
features). Showcase runs on the iOS simulator with the same flows as on Android. Setup, the
pieces and the iOS gate (`scripts/check-ios.sh`, macOS only): [docs/ios.md](docs/ios.md).

## Prerequisites

- JDK 17
- Android SDK 36 (`local.properties` with `sdk.dir`)
- Emulator `Medium_Phone_API_36.1` for instrumented tests
- Optional: a backend built from the backend starter. For local development that's
  `../backend-api` on port 3000; its seed creates the tenants `acme` and `globex`.

## Quick start

```sh
git config core.hooksPath .githooks          # once per clone: commit-message and pre-push checks
scripts/check.sh                             # unit tests + lint for every module, build-logic tests
./gradlew :apps:showcase:installDebug        # on a running emulator
./gradlew assembleDebug assembleRelease && scripts/verify-apps.sh   # build and verify every app
```

Debug builds talk to `http://10.0.2.2:3000/api/v1/`, the host machine as seen from the emulator.

**Signing in.** Showcase opens on sign-in, so start the local backend first
(`cd ../backend-api && docker compose up -d postgres && npm run db:deploy && npm run db:seed && npm run dev`).
The seed creates `alice@shared.test` and `bob@shared.test` (password `Password123!`) in its
tenants; showcase uses `acme`. Or create an account in the app.

**Android Studio:** pick the `showcase` run configuration (shared in `.run/`).
Release builds are unsigned unless the app has a `signing.properties`; see
[docs/release-signing.md](docs/release-signing.md).

## Documentation

- [Creating an app](docs/creating-an-app.md): start here for app #N
- [Creating a standalone app](docs/creating-a-standalone-app.md): an app in its own repo, with this
  foundation as a submodule
- [Architecture](docs/architecture.md): modules, dependency rules, how an app is put together
- [Adding a shared feature](docs/adding-a-shared-feature.md)
- [API integration](docs/api-integration.md): the backend contract, endpoints, errors and contract tests
- [Branding](docs/branding.md) · [API environments](docs/api-environments.md) ·
  [Release signing](docs/release-signing.md) · [Testing](docs/testing.md)
- [Design spec](docs/superpowers/specs/2026-11-10-reusable-app-foundation-design.md) ·
  [CLAUDE.md](CLAUDE.md) (git workflow)
