import XCTest
@testable import WhitelabelKit

/** The Debug launch arguments a service reads through LaunchFlags. */
final class LaunchFlagsTests: XCTestCase {

    override func tearDown() { DebugControls(arguments: []).applyLaunchFlags() }

    func testAnalyticsDebugAndConsentEEAAreReadFromTheArguments() {
        DebugControls(arguments: ["-analyticsDebug", "-consentEEA"]).applyLaunchFlags()
        XCTAssertTrue(LaunchFlags.analyticsDebug)
        XCTAssertTrue(LaunchFlags.consentEEA)

        DebugControls(arguments: []).applyLaunchFlags()
        XCTAssertFalse(LaunchFlags.analyticsDebug)
        XCTAssertFalse(LaunchFlags.consentEEA)
    }
}
