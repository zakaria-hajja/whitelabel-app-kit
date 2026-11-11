import XCTest

/** Drives the home tab: the greeting, the shortcuts and pull to refresh. */
struct HomeRobot {
    let app: XCUIApplication

    @discardableResult
    func open() -> Self {
        let tab = app.tabBars.buttons["Home"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10))
        tab.tap()
        return self
    }

    func greeting(_ text: String) -> XCUIElement { app.staticTexts[text] }

    /** A shortcut by the route of the tab it opens (the label may equal a tab's). */
    func shortcut(_ route: String) -> XCUIElement { app.buttons["shortcut.\(route)"] }

    func pullToRefresh() {
        let start = app.staticTexts.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        let end = start.withOffset(CGVector(dx: 0, dy: 400))
        start.press(forDuration: 0.05, thenDragTo: end)
    }
}
