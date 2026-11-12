import Foundation
import FirebaseAnalytics
import Shared

/** The shared `Analytics` logging to Firebase. Consent gating is applied around it in Kotlin. */
final class FirebaseAnalyticsAdapter: NSObject, Shared.Analytics {
    func track(event: AnalyticsEvent) {
        FirebaseAnalytics.Analytics.logEvent(event.name, parameters: event.params)
    }
}
