import AppTrackingTransparency
import Foundation
import Shared

/**
 * The shared `TrackingPort` over App Tracking Transparency. `AdsService` binds it when
 * `ads.ios.requestTracking` is on (`WL_ADS_TRACKING` yes); the shared `AdsController` calls it once
 * per launch, the first time a banner would load (after consent, never on launch 1). It asks only
 * while the user hasn't decided (`.notDetermined`), on the main actor, then calls `onDone`; a
 * decided status calls `onDone` at once. Requests never wait for the answer's meaning: the SDK reads
 * the status itself, and the foundation never forces non-personalized ads.
 */
public final class AttTrackingPort: NSObject, TrackingPort {
    private let status: @MainActor () -> ATTrackingManager.AuthorizationStatus
    private let request: @MainActor (@escaping () -> Void) -> Void

    public override convenience init() {
        self.init(
            status: { ATTrackingManager.trackingAuthorizationStatus },
            request: { done in ATTrackingManager.requestTrackingAuthorization { _ in done() } }
        )
    }

    /** [status] reads the current ATT status; [request] shows the prompt and calls back once answered. */
    init(
        status: @escaping @MainActor () -> ATTrackingManager.AuthorizationStatus,
        request: @escaping @MainActor (@escaping () -> Void) -> Void
    ) {
        self.status = status
        self.request = request
    }

    /** Only an undecided status is asked: iOS shows the prompt once, and later requests return at once. */
    static func shouldRequest(_ status: ATTrackingManager.AuthorizationStatus) -> Bool {
        status == .notDetermined
    }

    public func requestIfNeeded(onDone: @escaping () -> Void) {
        // Kotlin calls from its own threads; ATT is asked from the main actor.
        Task { @MainActor in
            guard Self.shouldRequest(status()) else { return onDone() }
            request(onDone)
        }
    }
}
