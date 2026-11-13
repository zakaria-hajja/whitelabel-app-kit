import AppTrackingTransparency
import XCTest
@testable import WhitelabelAds

/** ATT is asked only while undecided, on the main actor, and the controller always hears back. */
final class AttTrackingPortTests: XCTestCase {

    func testOnlyAnUndecidedStatusIsAsked() {
        XCTAssertTrue(AttTrackingPort.shouldRequest(.notDetermined))
        for status in [ATTrackingManager.AuthorizationStatus.authorized, .denied, .restricted] {
            XCTAssertFalse(AttTrackingPort.shouldRequest(status), "\(status.rawValue)")
        }
    }

    func testADecidedStatusSkipsThePromptAndIsDone() {
        var requests = 0
        let port = AttTrackingPort(status: { .denied }, request: { done in requests += 1; done() })
        let done = expectation(description: "done")
        port.requestIfNeeded { done.fulfill() }
        wait(for: [done], timeout: 2)
        XCTAssertEqual(requests, 0)
    }

    func testAnUndecidedStatusAsksOnTheMainThreadThenIsDone() {
        var askedOnMain = false
        let port = AttTrackingPort(status: { .notDetermined }, request: { done in askedOnMain = Thread.isMainThread; done() })
        let done = expectation(description: "done")
        // Kotlin calls from its own threads.
        DispatchQueue.global().async { port.requestIfNeeded { done.fulfill() } }
        wait(for: [done], timeout: 2)
        XCTAssertTrue(askedOnMain)
    }
}
