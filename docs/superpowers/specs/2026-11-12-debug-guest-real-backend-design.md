# Debug guest sign-in on the real backend

## Intent

The owner tests Showcase builds and wants to get past the sign-in gate in one tap, without typing a
password. Today **Continue as guest (debug)** appears only when the in-memory fake backend is linked
(`-Pwhitelabel.mockApi=true` on Android, `-mockApi` on iOS). A normal Debug build talks to the local
`backend-api`, so the button is missing there. This change makes it appear in every Debug build.

It's a developer convenience, not a product guest mode.

## Goals

- In every Debug build (Android and iOS), whether it runs on the fake or the real backend, the
  sign-in screen shows **Continue as guest (debug)**.
- On the real backend, the button signs in as the account that `npm run db:seed` creates:
  `alice@shared.test` / `Password123!`. These are the same credentials the fake uses.
- Release builds never contain the button or the credentials.

## Non-goals

- No backend or `/api/v1` contract changes, and no anonymous or guest accounts.
- No auto sign-in on launch. The sign-in, registration and reset screens stay reachable and
  testable.
- No pre-filled sign-in fields.

## Anti-goals

- Anything that would let a release build offer the button or ship the seeded password.

## Constraints

- `LoginViewModel` already takes an optional `DebugGuestAccount` (`getOrNull()` in
  `AuthenticationKoin`) and shows the button only when one is bound. That stays the switch.
- The account must come from Debug-only code, in the same way the fake reaches only
  `debugImplementation`. That keeps it out of release builds by construction, not just by a
  runtime flag.
- Logic belongs in shared (KMP) code. Swift only renders state and sends actions.

## Approaches

- **A. Bind the guest account in every Debug build (picked).** This reuses the existing button and
  wiring and keeps every screen testable. Trade-off: on a backend that hasn't been seeded, the tap
  shows the normal "wrong email or password" error.
- B. Auto sign-in on launch in Debug. Rejected: you can't reach the sign-in screens to test them,
  and signing out would sign you straight back in.
- C. Pre-fill the email and password in Debug. Rejected: it still needs a tap on Sign in, it gets
  in the way of testing validation, and it overlaps with the guest button.

## Design

- **Android:** a Debug-only Koin binding of `DebugGuestAccount`, added through the existing
  Debug-only path, for example a `src/debug` source set or a `debugImplementation`
  contributor found by `ServiceLoader` the way `:core:mockapi` is. It defaults to
  `alice@shared.test` / `Password123!`.
- **Android override (Claude's call):** optional `debug.guest.email` and `debug.guest.password`
  keys in the app's `local.properties` (untracked) replace the default, for a backend seeded with
  other accounts. Both must be set together, or the build fails with a clear message, following
  `AppConfigLoader`'s style.
- **Fake backend:** with the fake linked, the binding resolves to the fake's own seeded account, as
  it does today. There must be exactly one binding, so Koin never sees two definitions in
  conflict.
- **iOS:** Debug builds of the framework or app bind the same default account through the shared
  graph, so the button appears without `-mockApi`. Release doesn't. Claude's call: iOS gets the
  default only, with no override key, in this change.
- **Errors:** no new handling. A failed guest sign-in shows the same error as a failed manual
  sign-in.
- **Docs:** `docs/api-environments.md` and `docs/ios.md` say the button appears in every Debug
  build, which account it uses, that the real backend needs `db:seed`, and how to override it.

## Left to the builder

- The exact Debug-only mechanism on each platform (source set or contributor), as long as release
  builds can't link it.
- Tests: a Debug build binds the account and a Release build doesn't, the override keys work
  (including the error when only one is set), and the fake and the Debug binding don't conflict.
