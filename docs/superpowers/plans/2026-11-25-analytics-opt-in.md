# Analytics opt-in: send nothing when a user declines

Card: https://trello.com/c/ni4iAPsR (Tend board). Branch: `feat/analytics-opt-in`.

**Goal.** An app can choose *strict opt-in*: when the user declines analytics, Firebase Analytics
collection stays fully off. No events and no user properties are sent, either before or after
consent resolves. The switch defaults to off, so Munch and every other app keep today's
consent-mode behavior, which still counts a decliner without identifiers. Tend turns it on in a
later pointer bump.

**Scope.** Analytics only. Crashlytics keeps following the build as it does today; it is not
changed here.

## Tasks (test-first)

1. **`collectionFor`** (`core/firebase/shared/.../FirebaseCollection.kt`).
   - Add the parameter `analyticsOptIn: Boolean = false`.
   - When it is true and the state is `Resolved(analyticsAllowed = false)`, return `analytics = false` and `analyticsStorage = false`. `crashes` is unchanged.
   - All other cases stay as they are today.
   - commonTest covers each of these, flag on and flag off:
     - Obtained
     - Declined
     - NotRequired
     - Failed
     - Unknown
     - debug builds with and without the debug-analytics switch
2. **`ConsentGatedAnalytics`**. Add `analyticsOptIn: Boolean = false`. When it is true:
   - `track` forwards only when the state is `Resolved` and `analyticsAllowed`.
   - `setUserProperty` after resolution forwards only when `analyticsAllowed` (this settles N1).
   - The queue already drops on a decline; keep that.
   - Tests: flag off is unchanged, which the existing tests prove. Flag on with a decline sends nothing, including a property set after resolution. Withdrawing consent later stops sending. Granting it later starts sending.
3. **Wiring.**
   - `firebaseModule(mode, debugBuild, debugAnalytics, analyticsOptIn = false)` passes the flag to both `collectionFor` and the gates, in both ON and FAKE modes.
   - Android: add a field to the shell's Firebase wiring (ShellKoin `wiring`), defaulting to false, settable by the app (brand/app config, following the existing `debugAnalytics` pattern).
   - iOS: add the matching parameter to `SharedGraph`, default false, and expose it to Swift.
   - The Swift `FirebaseCollectionSwitch` needs no change, because it already applies `collection.analytics`. Check this, and add a Swift test only if the logic moves into Swift.
4. **Docs.** In `docs/firebase.md`:
   - Add a "Strict opt-in" subsection: what it does, how an app turns it on (Android and iOS), and the trade-off (decliners are not counted at all).
   - Update the "After a decline" paragraph and the "A user who declines is still counted" line so they name both modes.
5. **Gates.** `heavy` for every gradle, `check.sh`, `check-ios.sh` (if the foundation has one) and `git push`.
