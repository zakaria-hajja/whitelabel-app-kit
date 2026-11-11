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
}
