import Foundation
import FirebaseAnalytics
import FirebaseCrashlytics
import Shared

/** The Crashlytics calls the crash rule needs, so it can be tested without the SDK. */
protocol CrashCollection {
    /** Crashlytics' own (persisted) collection flag. */
    func isEnabled() -> Bool
    func setEnabled(_ enabled: Bool)
    func deleteUnsentReports()
}

private struct CrashlyticsCollection: CrashCollection {
    func isEnabled() -> Bool { Crashlytics.crashlytics().isCrashlyticsCollectionEnabled() }
    func setEnabled(_ enabled: Bool) { Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(enabled) }
    func deleteUnsentReports() { Crashlytics.crashlytics().deleteUnsentReports() }
}

/**
 * Android's crash rule: reports captured while collection was off are pre-consent and are dropped
 * when collection is first turned on; reports captured while it was on are kept (Crashlytics sends
 * a fatal crash on the next launch, so a consented session's report must survive the restart).
 * "Off" is Crashlytics' own persisted flag, read before it is set. Turning collection off deletes
 * nothing: a failed consent gather is a form or network error, not a refusal.
 */
struct CrashRule {
    let crash: CrashCollection

    func apply(enabled: Bool) {
        if enabled, !crash.isEnabled() { crash.deleteUnsentReports() }
        crash.setEnabled(enabled)
    }
}

/**
 * The shared `CollectionSwitch`: turns Firebase's collection on or off. The app's Info.plist starts
 * everything off; Kotlin applies nothing while consent is Unknown, so the persisted flags carry the
 * last consent applied.
 */
final class FirebaseCollectionSwitch: NSObject, CollectionSwitch {
    private let crashRule: CrashRule

    init(crash: CrashCollection = CrashlyticsCollection()) {
        crashRule = CrashRule(crash: crash)
    }

    func apply(collection: FirebaseCollection) {
        FirebaseAnalytics.Analytics.setAnalyticsCollectionEnabled(collection.analytics)
        // Only analytics storage follows the user's choice; ads signals stay denied (no ads in this target).
        FirebaseAnalytics.Analytics.setConsent([
            .analyticsStorage: collection.analyticsStorage ? .granted : .denied,
            .adStorage: .denied,
            .adUserData: .denied,
            .adPersonalization: .denied,
        ])
        crashRule.apply(enabled: collection.crashes)
    }
}
