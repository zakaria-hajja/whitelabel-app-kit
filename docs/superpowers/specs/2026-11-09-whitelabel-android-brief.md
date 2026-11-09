# Project brief: White-Label Android Starter Kit

The owner's brief (2026-11-09). Wording is unchanged except that a few short bullet lists
(§7, §10, §12–§15) are joined into sentences. The design that answers it is
[2026-11-09-whitelabel-android-design.md](./2026-11-09-whitelabel-android-design.md).

## 1. Project objective

Build a reusable, modular, production-ready Android application starter kit using Kotlin and Jetpack Compose.

The application will serve as the foundation for creating multiple independent white-label mobile applications for different clients.

Each client must receive a separate Android application (APK/AAB), with its own:
- Application name.
- Application ID / package name.
- Launcher icon.
- Splash screen.
- Brand colors.
- Typography and visual identity.
- API base URL.
- Feature configuration.

All applications must be generated from the same source code.

The objective is to avoid duplicating code or maintaining separate branches for different clients.

Adding a new client should primarily require adding configuration and branding assets.

This repository is exclusively responsible for the Android application. The backend is maintained in a separate repository.

## 2. Technology stack

- Kotlin.
- Jetpack Compose.
- Material 3.
- Kotlin Coroutines.
- Kotlin Flow.
- AndroidX Navigation Compose.
- Hilt for dependency injection.
- Retrofit or Ktor Client for networking.
- Kotlinx Serialization.
- Room for local persistence when necessary.
- DataStore for preferences.
- Gradle Kotlin DSL.
- Gradle Version Catalog.

Use stable, compatible dependency versions.

Follow modern Android development guidelines and best practices.

## 3. Architecture

Implement a modular Clean Architecture with MVVM and unidirectional data flow.

Separate the application into:

### Presentation
- Compose screens.
- Reusable UI components.
- ViewModels.
- Immutable UI states.
- UI events.

### Domain
- Business entities.
- Repository interfaces.
- Use cases.
- Business rules.

### Data
- Repository implementations.
- API services.
- DTOs.
- DTO-to-domain mapping.
- Local data sources.

Keep business logic outside composables.

Avoid unnecessary abstractions and excessive boilerplate.

## 4. Suggested module structure

Start with the following structure and refine it if necessary:

- `:app`
- `:core:common`
- `:core:network`
- `:core:database`
- `:core:datastore`
- `:core:designsystem`
- `:core:navigation`
- `:core:ui`
- `:core:branding`
- `:feature:authentication`
- `:feature:home`
- `:feature:catalog`
- `:feature:profile`
- `:feature:settings`

Each feature should be independently maintainable.

Avoid circular dependencies.

The application module should compose the features and configure application-level dependencies.

Provide a module dependency diagram.

## 5. White-label architecture

This is the most important requirement.

The project must support generating multiple independent applications using Gradle product flavors.

Introduce a `client` flavor dimension.

Example clients:

- `clientA`
- `clientB`

Example variants:

- `clientADebug`
- `clientARelease`
- `clientBDebug`
- `clientBRelease`

Each client must have its own:

- Application ID.
- App name.
- Launcher icon.
- Splash screen.
- Brand configuration.
- API base URL.
- Environment configuration.

Create a centralized, typed brand configuration system.

Do not scatter client-specific conditions throughout the feature modules.

Features must consume brand configuration through dependency injection or an equivalent centralized mechanism.

### Configuration separation

Support three configuration levels.

**Build-time configuration**
- Application ID.
- Application name.
- Launcher icon.
- Splash screen.
- API base URL.
- Environment.

**Runtime configuration**
- Feature availability.
- Support information.
- Privacy policy URL.
- Terms and conditions URL.
- Remote application settings.

**User preferences**
- Light/dark/system theme.
- Language.
- Notification preferences.

Build-time branding must not depend on runtime API responses.

The application should remain functional even if remote configuration cannot be retrieved.

## 6. Core features

### A. Authentication

- Login.
- Registration.
- Forgot password.
- Reset password.
- Logout.
- Authentication state management.
- Access token handling.
- Refresh token handling.
- Automatic token refresh.
- Session persistence.
- Protected navigation.

Support email/password authentication initially.

Store sensitive tokens securely using Android Keystore-backed encryption.

Handle expired sessions and refresh failures correctly.

### B. Home / Dashboard

- Welcome section.
- User information.
- Quick actions.
- Navigation to the main modules.
- Optional summary cards.

Keep the screen generic and customizable.

### C. List / Details

Implement a generic catalog feature demonstrating:

- Paginated lists.
- Search.
- Filtering.
- Sorting.
- Pull-to-refresh.
- Loading states.
- Empty states.
- Error states.
- Details screens.

Use a generic sample resource.

The feature must consume real backend APIs.

### D. Profile

- View profile.
- Edit profile.
- Change password.
- Display user information.

### E. Settings

- Theme selection.
- Language preferences.
- Notification preferences.
- About application.
- Privacy policy.
- Terms and conditions.
- Logout.

Persist user preferences appropriately.

## 7. Design system

Build a reusable design system using Jetpack Compose and Material 3.

Include: color palettes, typography, shapes, spacing, buttons, text fields, cards, dialogs,
loading indicators, error components, empty-state components, reusable screen layouts.

Support client-specific themes without changing feature implementations.

Support light, dark and system themes.

Prioritize consistency and accessibility.

## 8. Navigation

Implement centralized navigation.

- Authentication navigation graph.
- Main application navigation graph.
- Protected destinations.
- Bottom navigation.
- Deep-link-ready architecture.

Navigation should not be tightly coupled to individual feature implementations.

Authentication state must determine access to protected destinations.

## 9. Networking

Implement a centralized networking layer.

- Configurable base URL.
- Authentication headers.
- Access token management.
- Automatic refresh token handling.
- Consistent error mapping.
- Network error handling.
- DTO-to-domain mapping.
- Repository-based data access.

The API base URL must be supplied by the selected client and environment configuration.

Do not hardcode endpoints or URLs throughout feature modules.

Use a versioned REST API.

## 10. Backend integration

The backend is developed independently in a separate repository using Next.js and TypeScript.

The Android application must consume the backend's REST API.

Use the backend's OpenAPI specification as the API contract.

Document: expected API endpoints, request and response models, authentication requirements,
error responses, pagination conventions.

If the backend API is not available during development, use controlled mock implementations for development and testing.

Do not make production flows dependent on mock data.

## 11. Testing

- ViewModel unit tests.
- Use case tests.
- Repository tests.
- Navigation tests.
- Compose UI tests.
- Authentication flow tests.
- Networking tests.

Test multiple client variants.

Verify that each client receives the correct branding, package ID and API configuration.

## 12. CI/CD

Create GitHub Actions workflows to: build Android variants, run unit tests, run lint checks,
verify release builds.

Document how to build individual clients (Client A debug/release, Client B debug/release).

Signing credentials must never be committed.

## 13. Documentation

Provide: project architecture, module dependency diagram, setup instructions, build
instructions, client onboarding guide, branding customization guide, API integration
documentation, testing instructions, release and signing instructions.

The most important documentation should be:

**How to add a new client and generate its independent Android application without modifying existing feature implementations.**

## 14. Implementation strategy

Do not implement the entire application in one operation. Work in phases.

- **Phase 1: Architecture** — analyze requirements; propose module structure; define module
  dependencies, client flavor strategy, branding configuration, networking architecture;
  present an implementation roadmap.
- **Phase 2: Project foundation** — initialize Android project; configure Gradle; create core
  modules; implement design system; implement centralized branding.
- **Phase 3: Multi-client setup** — configure product flavors; add two sample clients;
  generate independent APKs; verify package IDs, icons, names and API URLs.
- **Phase 4: Core features** — authentication, home, list/details, profile, settings.
- **Phase 5: Integration and quality** — integrate the backend; add tests; configure CI/CD;
  complete documentation.

## 15. Agent instructions

Before writing code, present the proposed architecture and wait for approval.

Explain: why each module exists; module dependencies; how client configuration works; how
Gradle flavors will scale; how branding is injected; how networking is configured; how API
contracts are consumed.

Implement incrementally. After each phase: run relevant tests, verify the project builds,
report completed work, report outstanding issues, update documentation.

Avoid unnecessary abstractions and overengineering.

The final result must be a reusable Android starter kit capable of producing multiple
independent, branded applications from one maintainable Kotlin codebase.
