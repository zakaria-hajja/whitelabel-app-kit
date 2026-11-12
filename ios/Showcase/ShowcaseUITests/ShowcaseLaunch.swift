import XCTest

extension XCUIApplication {
    /**
     * Launches showcase as a fresh install on the fake backend, in English (a language picked in an
     * earlier test is stored in AppleLanguages, which iOS reads before the app's code runs).
     */
    func launchFresh(_ extra: [String] = []) {
        launchArguments = ["-mockApi", "-freshInstall", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"] + extra
        launch()
    }

    /** Launches again keeping what the last run stored (session, preferences, the fake's sessions). */
    func relaunch(_ extra: [String] = []) {
        terminate()
        launchArguments = ["-mockApi", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"] + extra
        launch()
    }

    /**
     * Opens a link of showcase's scheme in the running app, as a tap in Mail would.
     * (`XCUIApplication.open` relaunches the app with the same arguments, `-freshInstall` included.)
     */
    func openLink(_ link: String, file: StaticString = #filePath, line: UInt = #line) {
        XCUIDevice.shared.system.open(URL(string: link)!)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let confirm = springboard.buttons["Open"]
        if confirm.waitForExistence(timeout: 2) { confirm.tap() }
        _ = wait(for: .runningForeground, timeout: 5)
        // The app shows the last link it received (Debug probe): fail here rather than pass vacuously.
        XCTAssertTrue(probe("debug.lastLink", becomes: link), "the link never reached the app: \(link)", file: file, line: line)
    }

    /** Waits until a Debug probe's value is [value]. */
    func probe(_ id: String, becomes value: String, timeout: TimeInterval = 10) -> Bool {
        let element = staticTexts[id]
        let expectation = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == %@", value), object: element)
        return XCTWaiter.wait(for: [expectation], timeout: timeout) == .completed
    }

    /** The fake backend's recent requests (`401 PATCH /api/v1/settings | …`), read through `_fake/log`. */
    func fakeRequests() -> String {
        openLink("showcase://_fake/log")
        let log = staticTexts["debug.fakeLog"]
        _ = log.waitForExistence(timeout: 5)
        return log.label
    }
}
