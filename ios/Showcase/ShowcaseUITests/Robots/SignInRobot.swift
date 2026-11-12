import XCTest

/** Drives the sign-in screens, as Android's sign-in robot. */
struct SignInRobot {
    let app: XCUIApplication

    static let email = "alice@shared.test"
    static let password = "Password123!"

    var title: XCUIElement { app.staticTexts["Sign in to Showcase"] }

    @discardableResult
    func waitForSignIn(timeout: TimeInterval = 10) -> Bool { title.waitForExistence(timeout: timeout) }

    func signIn(_ email: String = Self.email, _ password: String = Self.password) {
        XCTAssertTrue(waitForSignIn())
        type(email, into: app.textFields["Email"])
        type(password, into: app.secureTextFields["Password"])
        app.buttons["Sign in"].tap()
    }

    /** Signs in and waits for the tabs. */
    func signInToTabs() {
        signIn()
        XCTAssertTrue(app.tabBars.buttons["Settings"].waitForExistence(timeout: 10))
        dismissSavePassword()
    }

    /**
     * After a successful sign-in iOS offers to save the password (right for users). The sheet
     * blocks taps for a moment, so tests answer it.
     */
    func dismissSavePassword() {
        let notNow = app.buttons["Not Now"]
        if notNow.waitForExistence(timeout: 3) { notNow.tap() }
    }

    func register(email: String, password: String) {
        XCTAssertTrue(waitForSignIn())
        app.buttons["Create an account"].tap()
        let field = app.textFields["Email"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        type(email, into: field)
        type(password, into: app.secureTextFields["Password"])
        app.buttons["Create account"].tap()
    }

    func type(_ text: String, into field: XCUIElement) {
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText(text)
    }
}
