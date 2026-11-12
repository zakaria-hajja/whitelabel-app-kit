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

    func testConsentTestDevicesAreReadFromTheArgument() {
        DebugControls(arguments: ["-consentEEA", "-consentTestDevice", "ABC123, DEF456"]).applyLaunchFlags()
        XCTAssertEqual(LaunchFlags.consentTestDevices, ["ABC123", "DEF456"])

        DebugControls(arguments: ["-consentTestDevice", "-consentEEA"]).applyLaunchFlags()
        XCTAssertEqual(LaunchFlags.consentTestDevices, [])

        DebugControls(arguments: ["-consentTestDevice"]).applyLaunchFlags()
        XCTAssertEqual(LaunchFlags.consentTestDevices, [])

        DebugControls(arguments: []).applyLaunchFlags()
        XCTAssertEqual(LaunchFlags.consentTestDevices, [])
    }
}
