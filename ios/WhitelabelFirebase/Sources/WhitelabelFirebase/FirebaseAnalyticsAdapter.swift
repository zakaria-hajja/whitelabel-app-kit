import Foundation
import FirebaseAnalytics
import Shared

/**
 * The shared `Analytics` logging to Firebase. Consent gating is applied around it in Kotlin. The
 * user-property call is passed in so the forwarding is testable without the SDK.
 */
final class FirebaseAnalyticsAdapter: NSObject, Shared.Analytics {
    private let sendUserProperty: (_ value: String, _ name: String) -> Void

    init(setUserProperty: @escaping (_ value: String, _ name: String) -> Void = { FirebaseAnalytics.Analytics.setUserProperty($0, forName: $1) }) {
        self.sendUserProperty = setUserProperty
    }

    func track(event: AnalyticsEvent) {
        FirebaseAnalytics.Analytics.logEvent(event.name, parameters: event.params)
    }

    func setUserProperty(property: UserProperty) {
        sendUserProperty(property.value, property.name)
    }
}
