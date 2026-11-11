import XCTest

/** Sign-in, registration and the session, end to end on the fake backend (as Android's showcase E2E). */
final class AuthUITests: XCTestCase {

    private let app = XCUIApplication()
    private var signIn: SignInRobot { SignInRobot(app: app) }
    private var settings: SettingsRobot { SettingsRobot(app: app) }

    override func setUp() { continueAfterFailure = false }

    func testSignInGreetsAndSignOutReturns() {
        app.launchFresh()
        signIn.signInToTabs()
        settings.open().signOut()
        XCTAssertTrue(signIn.waitForSignIn())
    }

    func testWrongPasswordStaysWithTheMessage() {
        app.launchFresh()
        signIn.signIn(SignInRobot.email, "wrong-password")
        XCTAssertTrue(app.staticTexts["Wrong email or password."].waitForExistence(timeout: 10))
        XCTAssertTrue(signIn.title.exists)
    }

    func testRegisterLandsSignedIn() {
        app.launchFresh()
        signIn.register(email: "new.user@shared.test", password: "Password123!")
        XCTAssertTrue(app.tabBars.buttons["Settings"].waitForExistence(timeout: 10))
        signIn.dismissSavePassword()
    }

    // MARK: Password reset

    func testForgotSendsTheLink() {
        app.launchFresh()
        XCTAssertTrue(signIn.waitForSignIn())
        app.buttons["Forgot password?"].tap()
        signIn.type(SignInRobot.email, into: app.textFields["Email"])
        app.buttons["Send link"].tap()
        XCTAssertTrue(app.staticTexts["If an account exists for this email, a reset link is on its way. Check your inbox."].waitForExistence(timeout: 10))
    }

    func testResetLinkResetsThePassword() {
        app.launchFresh(["-fakeResetToken", "t1"])
        XCTAssertTrue(signIn.waitForSignIn())
        app.openLink("showcase://reset-password?token=t1")
        XCTAssertTrue(app.staticTexts["Choose a new password"].waitForExistence(timeout: 10))
        signIn.type("BrandNew123!", into: app.secureTextFields["New password"])
        app.buttons["Save password"].tap()
        XCTAssertTrue(app.staticTexts["Your password has been changed. Sign in with your new password."].waitForExistence(timeout: 10))
        app.buttons["Back to sign in"].tap()
        signIn.signIn(SignInRobot.email, "BrandNew123!")
        XCTAssertTrue(app.tabBars.buttons["Settings"].waitForExistence(timeout: 10))
    }

    // MARK: Session edge cases

    /** An expired access token is refreshed behind the scenes: the next signed-in request just works. */
    func testExpiredTokenRefreshesSilently() {
        app.launchFresh()
        signIn.signInToTabs()
        settings.open()
        app.openLink("showcase://_fake/expire-tokens")
        let offers = app.switches["News and offers"]
        XCTAssertTrue(offers.waitForExistence(timeout: 10))
        offers.switches.firstMatch.tap()
        XCTAssertTrue(waitFor(offers, value: "1"))
        // As Android's E2E: the expired token was refused, refreshed once, and the request retried.
        XCTAssertTrue(app.fakeRequests().contains("401 PATCH /api/v1/settings | 200 POST /api/v1/auth/refresh | 200 PATCH /api/v1/settings"))
        XCTAssertFalse(signIn.title.exists)
        XCTAssertEqual(offers.value as? String, "1")
    }

    /** A session the server ended (signed out elsewhere) returns to sign-in at the next request. */
    func testRevokedSessionReturnsToSignIn() {
        app.launchFresh()
        signIn.signInToTabs()
        settings.open()
        app.openLink("showcase://_fake/revoke-sessions")
        let offers = app.switches["News and offers"]
        XCTAssertTrue(offers.waitForExistence(timeout: 10))
        offers.switches.firstMatch.tap()
        XCTAssertTrue(signIn.waitForSignIn())
    }

    /** Review Focus 2: a reset link is for signed-out users; while signed in it is ignored. */
    func testResetLinkWhileSignedInIsIgnored() {
        app.launchFresh(["-fakeResetToken", "t1"])
        signIn.signInToTabs()
        app.openLink("showcase://reset-password?token=t1")
        sleep(1)
        XCTAssertFalse(app.staticTexts["Choose a new password"].exists)
        XCTAssertTrue(app.tabBars.buttons["Settings"].exists)
        // Ignored, not held: signing out doesn't bring it back.
        settings.open().signOut()
        XCTAssertTrue(signIn.waitForSignIn())
        sleep(1)
        XCTAssertFalse(app.staticTexts["Choose a new password"].exists)
    }

    /** Review Focus 3: without the Keychain key (a reinstall, a restored backup) the token file can't be read: signed out, no crash. */
    func testUnreadableTokenFileStartsSignedOut() {
        app.launchFresh()
        signIn.signInToTabs()
        app.relaunch(["-dropTokenKey"])
        XCTAssertTrue(signIn.waitForSignIn())
    }

    func testRelaunchWithASessionStaysSignedIn() {
        app.launchFresh()
        signIn.signInToTabs()
        app.relaunch()
        XCTAssertTrue(app.tabBars.buttons["Settings"].waitForExistence(timeout: 10))
        sleep(3) // the start-up user refresh must not end the session
        XCTAssertFalse(signIn.title.exists)
        XCTAssertTrue(app.tabBars.buttons["Settings"].exists)
    }

    private func waitFor(_ element: XCUIElement, value: String, timeout: TimeInterval = 5) -> Bool {
        let expectation = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", value), object: element)
        return XCTWaiter.wait(for: [expectation], timeout: timeout) == .completed
    }
}
