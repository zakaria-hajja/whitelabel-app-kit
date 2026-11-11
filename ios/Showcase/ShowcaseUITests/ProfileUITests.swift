import XCTest

/** Profile: names round-trip to the home greeting, and the password change keeps the session on its errors. */
final class ProfileUITests: XCTestCase {

    private let app = XCUIApplication()
    private var profile: ProfileRobot { ProfileRobot(app: app) }

    override func setUp() {
        continueAfterFailure = false
        app.launchFresh()
        SignInRobot(app: app).signInToTabs()
    }

    func testEditNameAndHomeGreetsTheNewName() {
        profile.open().setFirstName("Ada")
        XCTAssertTrue(app.staticTexts["Profile saved."].waitForExistence(timeout: 10))
        HomeRobot(app: app).open()
        XCTAssertTrue(app.staticTexts["Hello, Ada"].waitForExistence(timeout: 10))
    }

    func testWrongCurrentPasswordStaysSignedIn() {
        profile.open().changePassword(current: "not-my-password", new: "Password456!", confirm: "Password456!")
        XCTAssertTrue(app.staticTexts["The current password is wrong."].waitForExistence(timeout: 10))
        XCTAssertTrue(app.tabBars.buttons["Profile"].exists)
    }

    func testTooShortShowsTheMinimum() {
        profile.open().changePassword(current: SignInRobot.password, new: "short", confirm: "short")
        XCTAssertTrue(app.staticTexts["Use at least 10 characters."].waitForExistence(timeout: 10))
    }
}
