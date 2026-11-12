import Foundation
import FirebaseCore
import FirebaseRemoteConfig
import Shared
import WhitelabelConsent
import WhitelabelKit

/**
 * Firebase (Remote Config, Analytics, Crashlytics) and the consent form for an iOS app, the twin of
 * Android's `firebase()`: list it in `AppDefinition(services: [FirebaseService()])`.
 * - `on`: configures Firebase, installs the Kotlin crash hook and hands the shared graph the UMP
 *   consent port and the Firebase ports. Collection stays off until consent resolves: the app's
 *   Info.plist must start it off (see `collectionOffKeys`), or the app stops at launch.
 * - `fake`: nothing; the shared graph binds the fakes (with `-fakeConsent`'s answer).
 * - `off`: nothing.
 */
public struct FirebaseService: AppService {
    public init() {}

    @MainActor public func install(mode: String) {
        guard mode == "on" else { return }
        if let problem = Self.collectionProblem(Bundle.main.infoDictionary ?? [:]) { fatalError(problem) }
        FirebaseApp.configure()
        KotlinCrashesKt.installKotlinCrashHook()
        #if DEBUG
        let debugBuild = true
        #else
        let debugBuild = false
        #endif
        SharedGraph.shared.useConsent(port: UmpConsentPort())
        SharedGraph.shared.useFirebase(
            source: FirebaseRemoteConfigSource(config: RemoteConfig.remoteConfig(), minimumFetchInterval: FetchInterval.seconds(debugBuild: debugBuild)),
            analytics: FirebaseAnalyticsAdapter(),
            crashReporter: FirebaseCrashReporter(),
            collection: FirebaseCollectionSwitch()
        )
    }

    /**
     * The Info.plist keys that keep Firebase from collecting before consent: Analytics and
     * Crashlytics collection off, and every Consent Mode default denied. Each must be `false`.
     */
    static let collectionOffKeys = [
        "FIREBASE_ANALYTICS_COLLECTION_ENABLED",
        "FirebaseCrashlyticsCollectionEnabled",
        "GOOGLE_ANALYTICS_DEFAULT_ALLOW_ANALYTICS_STORAGE",
        "GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_STORAGE",
        "GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_USER_DATA",
        "GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS",
    ]

    /** Why [info] would let Firebase collect before consent, or nil when every key is `false`. */
    static func collectionProblem(_ info: [String: Any]) -> String? {
        let wrong = collectionOffKeys.filter { (info[$0] as? Bool) != false }
        guard !wrong.isEmpty else { return nil }
        return "Firebase is on, but Info.plist doesn't start collection off until consent; set these to NO (Boolean): \(wrong.joined(separator: ", "))"
    }
}
