# White-Label Android Starter Kit

A white-label Android app foundation (Kotlin, Jetpack Compose, Hilt, Ktor). One codebase builds
many branded apps: each client is defined only by files under `clients/<id>/` (build config and
brand resources), and each becomes a Gradle product flavor. No Kotlin or Gradle file names a client.

Status: Phase 2 (foundation). Client onboarding docs arrive in Phase 3.

## Prerequisites

- JDK 17
- Android SDK 36 (`local.properties` with `sdk.dir`)
- Emulator `Medium_Phone_API_36.1` for instrumented tests
- Optional: `../backend-api` running locally (port 3000)

## Setup

```sh
git config core.hooksPath .githooks   # once per clone: commit-message and pre-push checks
scripts/check.sh                      # Gradle check (unit tests + lint, all modules) and build-logic tests
```

## Build

```sh
./gradlew :app:assembleClientADebug
./gradlew :app:assembleClientARelease   # unsigned
```

## Tests

```sh
./gradlew test                                        # unit tests
./gradlew :core:designsystem:connectedDebugAndroidTest   # instrumented; needs a running emulator
```

## Running against the local backend

Start `../backend-api` on port 3000. The debug build of clientA points at
`http://10.0.2.2:3000/api/v1/` (the emulator's alias for the host machine); cleartext is allowed
for that host in debug builds only. Install with `./gradlew :app:installClientADebug`.

## Modules

Built in Phase 2: `:app`, `:core:common`, `:core:network`, `:core:designsystem`,
`:core:branding`, plus `build-logic` (convention plugins and the client config loader).
Planned for Phase 4: `:core:domain`, `:core:data`, `:core:datastore`, `:core:ui`,
`:core:navigation` and the feature modules. See [docs/architecture.md](docs/architecture.md).

## Documentation

- [Architecture (Phase 2)](docs/architecture.md)
- [Design spec](docs/superpowers/specs/2026-11-09-whitelabel-android-design.md)
- [Foundation plan](docs/superpowers/plans/2026-11-09-whitelabel-android-foundation.md)
- [CLAUDE.md](CLAUDE.md): git workflow and project pointers
