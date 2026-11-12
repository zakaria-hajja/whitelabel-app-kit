import XCTest
import UserNotifications
@testable import WhitelabelKit

/** iOS's authorization states map to the three the apps act on; iOS asks only once, so there's no "denied, ask again". */
@MainActor
final class NotificationPermissionTests: XCTestCase {

    func testStatusesMapLikeTheDesign() {
        XCTAssertEqual(NotificationStatus.from(.notDetermined), .notAskedYet)
        XCTAssertEqual(NotificationStatus.from(.authorized), .granted)
        XCTAssertEqual(NotificationStatus.from(.provisional), .granted)
        XCTAssertEqual(NotificationStatus.from(.ephemeral), .granted)
        XCTAssertEqual(NotificationStatus.from(.denied), .blocked)
    }

    func testRequestThenRefreshReportsGranted() async {
        let center = FakeAuthorizing(status: .notDetermined, grants: true)
        let permission = NotificationPermission(center: center, arguments: [])
        await permission.refresh()
        XCTAssertEqual(permission.status, .notAskedYet)

        await permission.request()

        XCTAssertEqual(center.requests, 1)
        XCTAssertEqual(permission.status, .granted)
    }

    func testARefusalReportsBlocked() async {
        let permission = NotificationPermission(center: FakeAuthorizing(status: .notDetermined, grants: false), arguments: [])

        await permission.request()

        XCTAssertEqual(permission.status, .blocked)
    }

    func testDebugArgumentFixesTheStatusWithoutTheSystemAlert() async {
        let center = FakeAuthorizing(status: .authorized, grants: true)
        let permission = NotificationPermission(center: center, arguments: ["-notificationAccess", "blocked"])
        await permission.refresh()
        XCTAssertEqual(permission.status, .blocked)

        let askable = NotificationPermission(center: center, arguments: ["-notificationAccess", "notAskedYet"])
        await askable.refresh()
        await askable.request()
        XCTAssertEqual(askable.status, .granted)
        XCTAssertEqual(center.requests, 0)
    }
}

private final class FakeAuthorizing: NotificationAuthorizing, @unchecked Sendable {
    var status: UNAuthorizationStatus
    let grants: Bool
    var requests = 0

    init(status: UNAuthorizationStatus, grants: Bool) {
        self.status = status
        self.grants = grants
    }

    func authorizationStatus() async -> UNAuthorizationStatus { status }

    func requestAuthorization(options: UNAuthorizationOptions) async throws -> Bool {
        requests += 1
        status = grants ? .authorized : .denied
        return grants
    }
}
