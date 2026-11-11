import XCTest

/** The shell: the splash ends on the tabs, and a slow config never holds it past the shared wait. */
final class ShellUITests: XCTestCase {

    private let app = XCUIApplication()

    override func setUp() { continueAfterFailure = false }

    func testShowsTheSettingsTabAfterTheSplash() {
        app.launchFresh()
        XCTAssertTrue(app.tabBars.buttons["Settings"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.navigationBars["Settings"].exists)
    }

    /** Review Focus 1: the config answers after 5 s, but the shell stops waiting at 2 s (CONFIG_WAIT_MS). */
    func testSlowConfigStillShowsTabsWithin4Seconds() {
        app.launchFresh(["-fakeConfigDelayMs", "5000"])
        XCTAssertTrue(app.tabBars.buttons["Settings"].waitForExistence(timeout: 4))
    }
}
