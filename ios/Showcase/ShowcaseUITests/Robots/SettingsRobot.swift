import XCTest

/** Drives the settings tab, as Android's settings robot: tests say what the user does, not how to find it. */
struct SettingsRobot {
    let app: XCUIApplication

    @discardableResult
    func open() -> Self {
        let tab = app.tabBars.buttons["Settings"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10))
        tab.tap()
        return self
    }

    @discardableResult
    func pickTheme(_ name: String) -> Self {
        app.buttons["Theme"].tap()
        tapChoice(name)
        return self
    }

    @discardableResult
    func pickLanguage(_ name: String) -> Self {
        app.buttons["Language"].tap()
        tapChoice(name)
        return self
    }

    /** Signs out from the account section, confirming. */
    func signOut() {
        let button = app.buttons["Sign out"].firstMatch
        for _ in 0..<4 where !(button.exists && button.isHittable) { app.swipeUp() }
        button.tap()
        let confirm = app.alerts.buttons["Sign out"]
        XCTAssertTrue(confirm.waitForExistence(timeout: 5))
        confirm.tap()
    }

    func openLink(_ label: String) {
        let link = app.buttons[label]
        if !link.waitForExistence(timeout: 2) || !link.isHittable { app.swipeUp() }
        link.tap()
    }

    private func tapChoice(_ name: String) {
        let choice = app.buttons[name]
        XCTAssertTrue(choice.waitForExistence(timeout: 5))
        choice.tap()
    }
}
