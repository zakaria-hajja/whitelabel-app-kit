# API integration

The shared features call the **backend-starter API** directly: every app's backend is built from
the same starter as `../backend-api`, so one contract serves them all. This page is what the app
expects from it. The machine-readable contract is `api/openapi.json`.

## The contract in this repo

- `api/openapi.json` is the backend's generated OpenAPI 3.1 spec at a tagged release, and
  `api/SOURCE` records which: `tag`, `commit` and the file's `sha256`. Currently **v1.1.1**.
- Never edit it by hand. CI runs `scripts/check-openapi.sh`, which fails when the file no longer
  matches `api/SOURCE`.
- **Moving to a new backend tag:** `scripts/sync-openapi.sh v1.2.0` (reads `../backend-api`, next to
  this repo, or pass the backend directory as a second argument; it always updates this repo's `api/`,
  wherever you run it from), then `scripts/check.sh`. The contract tests below fail wherever the app
  no longer matches what they check; fix those, then open a PR. CI can't see the backend repo, so
  noticing that a new tag exists is up to us (the backend's release notes list contract additions).
- The backend promises that `/api/v1` only grows (`../backend-api/docs/contract.md`): new
  endpoints, fields, error codes and flag keys may appear; nothing is removed or retyped within v1.
  The app decodes with `ignoreUnknownKeys`, maps unknown error codes to `UNKNOWN`, and reads enum-like
  values (theme) as strings, so a newer backend never breaks an installed app.

## Endpoints the app calls

All paths are under the app's `api.*.baseUrl` (`…/api/v1/`). Features pass relative paths.

| Endpoint | Auth | Called by |
|---|---|---|
| `GET app/config` | public | `:core:data` (`DefaultAppConfigRepository`), at start |
| `GET health` | public | `:core:shell` diagnostics (debug builds) |
| `POST auth/login`, `auth/register`, `auth/logout` | public | `:core:data` (`DefaultSessionRepository`) |
| `POST auth/refresh` | public | `:core:network` (`SessionAuth`), on its own |
| `POST auth/forgot-password`, `auth/reset-password` | public | `:feature:authentication` |
| `GET users/me`, `PATCH users/me` | bearer | `:core:data` (`DefaultUserRepository`) |
| `PATCH users/me/password` | bearer | `:feature:profile` |
| `GET settings`, `PATCH settings` | bearer | `:feature:settings` |
| `GET catalog`, `GET catalog/{id}` | bearer | `:feature:catalog` |

Request and response models are the spec's schemas; each module keeps its own small `@Serializable`
DTOs for the fields it uses.

## Envelope, errors and pagination

- Success: `{"success": true, "data": …, "meta": {"requestId": …}}`. Errors:
  `{"success": false, "error": {"code", "message", "details"?}, "meta": …}`.
- `:core:network`'s `getResult`, `getPageResult`, `postResult`, `patchResult` and `deleteResult` unwrap
  the envelope and return `Result<T, DataError>`:
  - a backend error → `DataError.Api(code, httpStatus, fieldErrors, retryAfterSeconds)`, with `code`
    from the error envelope (unknown codes → `UNKNOWN`);
  - no network → `DataError.Network.NO_INTERNET`; timeout → `REQUEST_TIMEOUT`; an unreadable body →
    `SERIALIZATION`.
- Screens show `DataError.toUiText()` (`:core:ui`), after handling the codes they treat specially:
  `INVALID_CREDENTIALS` (wrong password), `EMAIL_ALREADY_REGISTERED`, `INVALID_RESET_TOKEN`,
  `FEATURE_DISABLED`, `NOT_FOUND`. `RATE_LIMITED` shows the `Retry-After` seconds.
- **Pagination:** `page` ≥ 1, `pageSize` 1–100 (default 20); the response's
  `meta.pagination{page, pageSize, totalItems, totalPages}`; a page past the end is empty.

## Authentication

- Requests carry `Authorization: Bearer <access token>` while signed in. Sign-in, registration,
  refresh, logout and password reset are `publicRequest()`s and never do.
- `SessionAuth` follows the backend's client guidance: on 401 `TOKEN_EXPIRED` or `UNAUTHENTICATED`
  it refreshes once (shared by concurrent calls) and retries once; a rejected refresh, or a retry still
  rejected, ends the session; a refresh with no response is retried once at once with the same token.
  Every other 401 (`INVALID_CREDENTIALS`) is returned as is. The new pair is read from
  `data.tokens`.
- Tokens are stored encrypted (`:core:datastore`'s `TokenStore`, AES-GCM with a Keystore key).

## Tenant header

`X-Client-Id: <api.tenant>` is sent when the app sets `api.tenant` (shared-mode backends); dedicated
backends need none. See [api-environments.md](api-environments.md).

## Contract tests

Each module that calls the API has a `ContractTest` (JVM). It points the module's real data source at
`ContractBackend` (`:core:testing`), which answers with the spec's own example for that operation,
and checks:

- the data source decodes the example (and a field or two of it), and also the **smallest** response
  the schema allows (`ContractBackend(minimal = true)`: optional fields left out, nullable ones `null`);
- the request body it sent validates against the spec's request schema
  (`backend.assertRequestMatches(method, path)`);
- every request, automatically: query parameters are declared for the operation and fit their schemas;
  a secured operation carries the bearer; a public write (sign-in, refresh, reset…) does not;
- in `:core:network`: every code in the contract's error enum is known to the app, and every documented
  error example maps to the code it names.

Not checked: string formats (`uuid`, `date-time`, `email`, `uri`) are not asserted, and response
headers such as `Retry-After` are not part of the examples.

**An app's own endpoints** (`/api/v1/x/<feature>/…`) belong to that app's backend contract. To test
them the same way, vendor that backend's spec into the app repo (e.g. `api/openapi.json` there), and
point the app module's tests at it, after the convention plugin has pointed them at the foundation's:

```kotlin
// apps/<name>/<feature>/build.gradle.kts
tasks.withType<Test>().configureEach {
    systemProperty("whitelabel.openapi", rootProject.file("api/openapi.json").absolutePath)
}
```

`OpenApiSpec` reads one spec per test run, so a module tests against either the foundation's contract
or its app's, not both.
