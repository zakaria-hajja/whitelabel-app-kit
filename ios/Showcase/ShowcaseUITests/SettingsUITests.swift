import XCTest

/** Settings on the fake backend: theme applies at once, a language waits for the next launch and says so, legal links open Safari. */
final class SettingsUITests: XCTestCase {

    private let app = XCUIApplication()
    private var settings: SettingsRobot { SettingsRobot(app: app) }

    override func setUp() {
        continueAfterFailure = false
        app.launchFresh()
        SignInRobot(app: app).signInToTabs()
    }

    func testThemeChangeAppliesAtOnce() {
        settings.open().pickTheme("Dark")
        let probe = app.staticTexts["debug.colorScheme"]
        XCTAssertTrue(probe.waitForExistence(timeout: 5))
        XCTAssertEqual(probe.label, "dark")
        XCTAssertEqual(app.buttons["Theme"].value as? String, "Dark")
    }

    /** Review Focus 5: iOS applies an app language at the next launch; the screen says so and stays in the launch language. */
    func testLanguageChangeSaysItAppliesOnRestart() {
        settings.open().pickLanguage("Français")
        XCTAssertTrue(app.staticTexts["Takes effect the next time you open the app."].waitForExistence(timeout: 5))
        XCTAssertTrue(app.navigationBars["Settings"].exists)
        XCTAssertEqual(app.buttons["Language"].value as? String, "Français")
    }

    func testLegalLinkOpensSafari() {
        settings.open().openLink("Privacy policy")
        let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
        XCTAssertTrue(safari.wait(for: .runningForeground, timeout: 15))
    }
}
