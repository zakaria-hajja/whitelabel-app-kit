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
}
