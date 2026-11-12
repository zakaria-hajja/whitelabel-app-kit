import XCTest

/**
 * Each test starts as a fresh install (`-freshInstall`), as Android's FreshAppRule gives each test:
 * the first test leaves a session, the second (run after it, alphabetically) still starts signed out.
 */
final class CleanStartUITests: XCTestCase {

    private let app = XCUIApplication()

    override func setUp() { continueAfterFailure = false }

    func testA_signsInAndLeavesASession() {
        app.launchFresh()
        SignInRobot(app: app).signInToTabs()
    }

    func testB_stillStartsSignedOut() {
        app.launchFresh()
        XCTAssertTrue(SignInRobot(app: app).waitForSignIn())
        XCTAssertFalse(app.tabBars.buttons["Settings"].exists)
    }
}
