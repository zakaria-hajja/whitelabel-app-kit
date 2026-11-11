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

    func testATextFieldAnnouncesItsError() {
        let field = app.secureTextFields["Password"].firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        XCTAssertEqual(field.value as? String, "Use at least 10 characters.")
    }
}
