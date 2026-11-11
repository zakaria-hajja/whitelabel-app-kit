import XCTest

/** The shell: the splash ends on the app (sign-in, as showcase has a gate), and a slow config never holds it past the shared wait. */
final class ShellUITests: XCTestCase {

    private let app = XCUIApplication()

    override func setUp() { continueAfterFailure = false }

    func testShowsSignInAfterTheSplash() {
        app.launchFresh()
        XCTAssertTrue(SignInRobot(app: app).waitForSignIn())
    }

    /** Review Focus 1: the config answers after 5 s, but the shell stops waiting at 2 s (CONFIG_WAIT_MS). */
    func testSlowConfigStillShowsTheAppWithin4Seconds() {
        app.launchFresh(["-fakeConfigDelayMs", "5000"])
        XCTAssertTrue(SignInRobot(app: app).waitForSignIn(timeout: 4))
    }
}
