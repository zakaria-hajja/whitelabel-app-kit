import XCTest
import Shared
@testable import WhitelabelFirebase

/** `FirebaseService(analyticsOptIn: true)` hands strict opt-in to the shared graph, Android's `firebase(analyticsOptIn = true)`. */
@MainActor
final class StrictOptInServiceTests: XCTestCase {

    override func setUp() { SharedGraph.shared.stop(); SharedGraph.shared.clearFirebaseServices() }
    override func tearDown() { SharedGraph.shared.stop(); SharedGraph.shared.clearFirebaseServices() }

    func testTheDefaultServiceLeavesStrictOptInOff() {
        FirebaseService().install(mode: "fake")
        XCTAssertFalse(SharedGraph.shared.isAnalyticsOptIn())
    }

    // `on` calls FirebaseApp.configure(), which stops a test process without GoogleService-Info.plist;
    // the flag is set before that branch, the same way for every mode.
    func testAnalyticsOptInReachesTheGraphInOffAndFakeModes() {
        for mode in ["off", "fake"] {
            SharedGraph.shared.clearFirebaseServices()
            FirebaseService(analyticsOptIn: true).install(mode: mode)
            XCTAssertTrue(SharedGraph.shared.isAnalyticsOptIn(), mode)
        }
    }
}
