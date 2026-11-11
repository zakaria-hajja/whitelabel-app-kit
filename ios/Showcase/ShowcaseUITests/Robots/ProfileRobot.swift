import XCTest

/** Drives the profile tab and the password change. */
struct ProfileRobot {
    let app: XCUIApplication

    @discardableResult
    func open() -> Self {
        let tab = app.tabBars.buttons["Profile"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10))
        tab.tap()
        return self
    }

    func setFirstName(_ name: String) {
        let field = app.textFields["First name"]
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.tap()
        let old = (field.value as? String) ?? ""
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: old.count + 2) + name)
        app.buttons["Save"].tap()
    }

    func changePassword(current: String, new: String, confirm: String) {
        app.buttons["Change password"].firstMatch.tap()
        let robot = SignInRobot(app: app)
        robot.type(current, into: app.secureTextFields["Current password"])
        robot.type(new, into: app.secureTextFields["New password"])
        robot.type(confirm, into: app.secureTextFields["Confirm new password"])
        let submit = app.buttons.matching(identifier: "Change password").element(boundBy: 0)
        submit.tap()
    }
}
