import XCTest

/**
 * The kit's content gate on a relock (review B1): a sheet and a full-screen cover that a screen of the
 * app has open when the app returns locked never show over the lock. Showcase runs behind
 * `UiTestContentGate` (`-uiTestContentGate`), which locks on every return to the foreground.
 */
final class ContentGateUITests: XCTestCase {

    private let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
        app.launchFresh(["-uiTestContentGate"])
        SignInRobot(app: app).signInToTabs()
    }

    func testRelock_coversOpenSheetAndCover_andUnlockReturnsToTheApp() {
        // iOS's save-password prompt can arrive at any point here and swallows taps.
        SignInRobot(app: app).dismissSavePassword()
        SettingsRobot(app: app).open()
        let openSheet = app.buttons["Open probe sheet"]
        let notNow = app.buttons["Not Now"]
        for _ in 0..<8 where !(openSheet.exists && openSheet.isHittable) {
            if notNow.waitForExistence(timeout: 1) {
                notNow.tap()
                SettingsRobot(app: app).open()
            } else {
                app.swipeUp()
            }
        }
        openSheet.tap()
        XCTAssertTrue(app.staticTexts["probe.sheet"].waitForExistence(timeout: 5))
        app.buttons["Open probe cover"].tap()
        let cover = app.staticTexts["probe.cover"]
        XCTAssertTrue(cover.waitForExistence(timeout: 5))

        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 10))
        app.activate()

        // The lock is on top from the first look: nothing of the app is hittable above it.
        let lock = app.staticTexts["uitest.lock"]
        XCTAssertTrue(lock.waitForExistence(timeout: 5))
        XCTAssertTrue(lock.isHittable, "the lock is covered")
        // The first frame after returning: no sheet or cover over the lock (UI tests can't see frames;
        // the Debug probe records the topmost window on the first display-link tick).
        XCTAssertTrue(app.probe("uitest.firstFrame", becomes: "lock-on-top", timeout: 5),
                      "first frame: \(app.staticTexts["uitest.firstFrameDetail"].label)")
        XCTAssertFalse(cover.exists && cover.isHittable, "the full-screen cover shows over the lock")
        XCTAssertFalse(app.staticTexts["probe.sheet"].exists && app.staticTexts["probe.sheet"].isHittable, "the sheet shows over the lock")
        // Then the presentations are gone, not just covered: the content under the lock was removed.
        XCTAssertTrue(cover.waitForNonExistence(timeout: 5), "the cover still exists while locked")
        XCTAssertTrue(app.staticTexts["probe.sheet"].waitForNonExistence(timeout: 5), "the sheet still exists while locked")
        XCTAssertTrue(lock.exists)

        app.buttons["Unlock"].tap()
        XCTAssertTrue(lock.waitForNonExistence(timeout: 5))
        XCTAssertTrue(app.tabBars.buttons["Settings"].waitForExistence(timeout: 5))
    }
}
