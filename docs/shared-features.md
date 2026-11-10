# Shared features

Each shared feature is one module under `feature/` and one call in an app's definition. This page
lists what each one does, its options, its flag key, the backend endpoints it uses and the
analytics events it sends. To add a feature to an app, add the module to the app's
`build.gradle.kts` and call it in the definition. To write a new one, see
[adding-a-shared-feature.md](adding-a-shared-feature.md).

| Feature | Call | Flag key | Needs `auth()` |
|---|---|---|---|
| Authentication | `auth()` | none | n/a (it is the gate) |

## Authentication (`:feature:authentication`)

Makes the **whole app sign-in only**. Signed out, the app shows only the sign-in screens. After
sign-in or registration, the app's tabs appear with nothing to go back to. Signing out, or the
session ending on the server, returns to sign-in.

```kotlin
auth(AuthOptions(allowRegistration = true, allowPasswordReset = true))
```

| Option | Default | Effect |
|---|---|---|
| `allowRegistration` | `true` | Shows "Create an account" on the sign-in screen |
| `allowPasswordReset` | `true` | Shows "Forgot password?" on the sign-in screen |

- **Screens:** sign in, create an account (email, password, optional first and last name), forgot
  password, and reset password.
- **Rules (from the backend):** passwords are 10–128 characters to register or reset, and never
  trimmed. Names are at most 50 characters. Emails are trimmed. The forgot-password screen says the
  same thing whether or not the account exists.
- **Reset link:** `<app.deepLinkScheme>://reset-password?token=…`. The tenant's
  `passwordReset.linkBaseUrl` must be `<scheme>://reset-password` (see
  [api-environments.md](api-environments.md#password-reset-links)). It opens only while signed out.
- **Deep links into the app:** while signed out they wait, and open right after sign-in.
- **Endpoints:** `POST auth/login`, `auth/register`, `auth/logout`, `auth/forgot-password`,
  `auth/reset-password`, and `auth/refresh` (by `:core:network`).
- **Analytics:** `login` and `sign_up`, each with `{method: password}` only.
