import XCTest

/** The design-system catalog (Debug, `-designCatalog`): both packs render, and their controls read well with VoiceOver. */
final class DesignCatalogUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-designCatalog"]
        app.launch()
    }

    func testOpensWithBothPacks() {
        XCTAssertTrue(app.staticTexts["Gallery"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Material"].exists)
        XCTAssertTrue(app.buttons["Primary action"].firstMatch.exists)
    }

    func testAButtonIsAnnouncedByItsLabel() {
        XCTAssertTrue(app.buttons["Primary action"].firstMatch.waitForExistence(timeout: 10))
    }

    /** VoiceOver reads what was typed as the field's value, and the error as its own element right after. */
    func testATextFieldKeepsItsTextAndAnnouncesItsError() {
        let email = app.textFields["Email"].firstMatch
        XCTAssertTrue(email.waitForExistence(timeout: 10))
        XCTAssertEqual(email.value as? String, "ada@example.com")
        XCTAssertTrue(app.staticTexts["Use at least 10 characters."].firstMatch.exists)
    }
}
