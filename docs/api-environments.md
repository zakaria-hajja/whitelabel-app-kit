# API environments

Every app talks to its own backend, built from the backend starter (`../backend-api`). They all
share one contract, `/api/v1`:
- the response envelope
- the error codes
- the auth endpoints
- the paging rules

The shared networking and (Phase 4) shared features target that contract directly. An app only
says where its backend is.

## Base URLs per build type

| Build type | Key | Environment | Rules |
|---|---|---|---|
| `debug` | `api.debug.baseUrl` | `DEVELOPMENT` | `http` only to `10.0.2.2` (the host machine from the emulator) or `localhost`; any `https` host |
| `release` | `api.release.baseUrl` | `PRODUCTION` | `https`, not a local host |

- **URL form:** the URL is the versioned root, ending in `/` (added if missing). Features use
  relative paths such as `"catalog"`, which resolve under it.
- **Cleartext:** debug builds allow cleartext only for those two hosts
  (`core/shell/src/debug/res/xml/network_security_config.xml`). The loader rejects a debug
  `http` URL to any other host, so the app can't fail at runtime with "cleartext not permitted".
- **On a physical device:** use an `https` tunnel, or `adb reverse tcp:3000 tcp:3000` with
  `http://localhost:3000/api/v1/`.
- **Adding a staging environment later:** add a `staging` build type (`initWith(release)`) and an
  `api.staging.baseUrl` key in the loader. Don't add a second flavor dimension.

## Tenant (shared vs dedicated backends)

- **Shared mode** (several tenants on one deployment): set `api.tenant=<slug>`. Every request
  sends `X-Client-Id: <slug>`, and the tenant must exist in that backend
  (`npm run tenant -- create`).
- **Dedicated mode** (one tenant per deployment): leave `api.tenant` out. No header is sent, and
  the backend uses its configured tenant.

## Password reset links

The backend emails `<linkBaseUrl>?token=…`, where `linkBaseUrl` is the tenant's
`passwordReset.linkBaseUrl`. For the link to open the app's reset screen, set it to
`<app.deepLinkScheme>://reset-password`, for example `showcase://reset-password`. Use
`npm run tenant -- set-config` in shared mode, or the deployment's config JSON in dedicated mode
(see the backend's `docs/deployment-*.md`). The app accepts the link only while signed out.

## Local backend

```sh
cd ../backend-api
docker compose up -d postgres && npm run db:deploy && npm run db:seed   # tenants acme, globex
npm run dev                                                             # http://localhost:3000
```

Showcase (tenant `acme`) points its debug build at
`http://10.0.2.2:3000/api/v1/`. In debug builds, Settings › About › Diagnostics has a **Ping** that calls `GET health` and shows
whether the backend and its database are up.

## Working without the backend

Pass `-Pwhitelabel.mockApi=true` to build debug variants against `:core:mockapi`, an in-memory
backend that follows the vendored contract (`api/openapi.json`):

```sh
./gradlew -Pwhitelabel.mockApi=true :apps:showcase:installDebug
```

To keep it on in Android Studio, add `whitelabel.mockApi=true` to `~/.gradle/gradle.properties`
(not the repo's file, so nobody commits it). The fake has:

- the seeded users `alice@shared.test` and `bob@shared.test`, password `Password123!`, plus any you register;
- 25 catalog items (`Item 1`…`Item 25`, categories Books, Games, Music and Travel), with paging, search, filters and sorts;
- 15-minute access tokens and single-use refresh tokens, like the real backend;
- the documented error codes (wrong password, short password, a taken email, unknown items).

State lasts until the app process ends. A reset link can't be emailed; in tests,
`FakeBackend.lastResetToken` holds the token. Diagnostics shows **Backend: Mock (in memory)**,
and the API line still shows the configured URL, which is never called.

The flag only ever adds a `debugImplementation`, so release builds can't contain the mock, and
`scripts/verify-apps.sh` fails any release APK whose dex has it. Any other value, or none, means
the real backend.

## Requests and errors (for feature authors)

- **Helpers:** use `HttpClient.getResult`, `getPageResult`, `postResult`, `patchResult` and
  `deleteResult` from `:core:network`. They return `Result<T, DataError>` with the envelope
  already unwrapped.
- **Backend errors:** these arrive as `DataError.Api(code, httpStatus, fieldErrors,
  retryAfterSeconds)`, with `code` taken from the backend's error codes.
- **Transport failures:** these become `DataError.Network` (`NO_INTERNET`, `REQUEST_TIMEOUT`,
  `SERIALIZATION`, `UNKNOWN`).
- **Logging:** request logging (headers only, `Authorization` redacted) is on in `DEVELOPMENT`
  only.
