import XCTest

/** Home: greets the signed-in user, opens tabs from its shortcuts, and says why a refresh failed. */
final class HomeUITests: XCTestCase {

    private let app = XCUIApplication()
    private var home: HomeRobot { HomeRobot(app: app) }

    override func setUp() {
        continueAfterFailure = false
        app.launchFresh()
        SignInRobot(app: app).signInToTabs()
    }

    func testGreetsByFirstName() {
        XCTAssertTrue(home.greeting("Hello, Alice").waitForExistence(timeout: 10))
    }

    func testShortcutOpensSettings() {
        let shortcut = home.shortcut("settings")
        XCTAssertTrue(shortcut.waitForExistence(timeout: 10))
        shortcut.tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
    }

    func testRefreshOfflineShowsTheMessage() {
        XCTAssertTrue(home.greeting("Hello, Alice").waitForExistence(timeout: 10))
        app.openLink("showcase://_fake/offline")
        home.pullToRefresh()
        XCTAssertTrue(app.staticTexts["No internet connection. Check your connection and try again."].waitForExistence(timeout: 10))
    }
}
