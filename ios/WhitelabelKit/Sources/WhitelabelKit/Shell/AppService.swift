import Foundation

/**
 * A platform service an app installs into the shared graph before it starts (Firebase and the
 * consent form, or Google Mobile Ads, which an app target links and WhitelabelKit never imports).
 * The app lists its services in `AppDefinition(services:)`. Ports are callback-based: Kotlin never
 * suspends on Swift.
 *
 * Its [kind] says which setting it follows and so which `mode` it gets:
 * - `.firebase` (`FirebaseService`, the twin of Android's `firebase()`): `WL_FIREBASE`, `on`, `fake`
 *   or `off` (`fake` also when the Debug argument `-fakeFirebase` asks for it). In `on` it calls
 *   `SharedGraph.shared.useConsent` and `useFirebase`;
 * - `.ads` (`AdsService`, the twin of `:core:ads:google`): `WL_ADS`, `on` or `off`. In `on` it calls
 *   `useConsent` and `useAds` and registers the banner host.
 *
 * In any other mode a service installs nothing (the fakes, the defaults-only values and hidden
 * banners come from Kotlin).
 */
public protocol AppService {
    var kind: AppServiceKind { get }
    @MainActor func install(mode: String)
}

/** Which app setting an [AppService] follows. */
public enum AppServiceKind: Sendable {
    case firebase
    case ads
}

/** Debug launch arguments a service may read (always false in Release). */
public enum LaunchFlags {
    /** `-consentEEA`: the consent form behaves as in the EEA. */
    public internal(set) static var consentEEA = false
    /** `-consentTestDevice <ids>`: UMP test devices (UMP honors the EEA geography only on these). */
    public internal(set) static var consentTestDevices: [String] = []
    /** `-analyticsDebug`: collection runs in this debug build and events go to Firebase's DebugView. */
    public internal(set) static var analyticsDebug = false
}
