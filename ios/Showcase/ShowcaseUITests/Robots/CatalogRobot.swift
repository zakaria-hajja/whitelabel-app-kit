import XCTest

/** Drives the catalog, as Android's catalog robot. */
struct CatalogRobot {
    let app: XCUIApplication

    @discardableResult
    func open() -> Self {
        let tab = app.tabBars.buttons["Catalog"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10))
        tab.tap()
        return self
    }

    func search(_ text: String) {
        let field = app.searchFields["Search"]
        if !field.waitForExistence(timeout: 3) { app.swipeDown() }
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText(text)
    }

    func filter(_ category: String) {
        app.buttons[category].firstMatch.tap()
    }

    func sortBy(_ label: String) {
        app.buttons["Sort"].firstMatch.tap()
        let choice = app.buttons[label]
        XCTAssertTrue(choice.waitForExistence(timeout: 5))
        choice.tap()
    }

    func row(_ title: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", title + ",")).firstMatch
    }

    /** The rows appear top to bottom in this order. */
    func showsInOrder(_ titles: String..., file: StaticString = #filePath, line: UInt = #line) {
        let rows = titles.map(row)
        for (title, row) in zip(titles, rows) {
            XCTAssertTrue(row.waitForExistence(timeout: 10), "missing \(title)", file: file, line: line)
        }
        let tops = rows.map(\.frame.minY)
        XCTAssertEqual(tops, tops.sorted(), "not in order: \(titles)", file: file, line: line)
    }

    func doesNotSee(_ title: String, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertFalse(row(title).exists, "still shows \(title)", file: file, line: line)
    }

    func open(_ title: String) { row(title).tap() }

    func showsDetail(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(app.staticTexts[text].waitForExistence(timeout: 10), "no detail \(text)", file: file, line: line)
    }
}
