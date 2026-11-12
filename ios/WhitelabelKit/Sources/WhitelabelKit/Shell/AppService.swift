import Foundation

/**
 * A platform service an app installs into the shared graph before it starts (for example Firebase
 * and the consent form, which an app target links and WhitelabelKit never imports). The app lists
 * its services in `AppDefinition(services:)`; having one is the iOS twin of Android's `firebase()`.
 * Ports are callback-based: Kotlin never suspends on Swift.
 *
 * `mode` is the app's `WL_FIREBASE` setting (`on`, `fake` or `off`; `fake` also when the Debug
 * argument `-fakeFirebase` asks for it). In `fake` and `off` a service installs nothing (the fakes
 * and the defaults-only values come from Kotlin); in `on` it calls `SharedGraph.shared.useConsent`
 * and `useFirebase`.
 */
public protocol AppService {
    @MainActor func install(mode: String)
}

/** Debug launch arguments a service may read (always false in Release). */
public enum LaunchFlags {
    /** `-consentEEA`: the consent form behaves as in the EEA. */
    public internal(set) static var consentEEA = false
}
