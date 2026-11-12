import XCTest
@testable import WhitelabelKit

/** About is one line, as on Android: name and version, then the app's note. */
final class SettingsAboutLineTests: XCTestCase {

    func testNameVersionAndNote() {
        XCTAssertEqual(SettingsList.aboutLine(appName: "Munch", version: "0.1.0", note: "Your data stays on this phone."),
                       "Munch 0.1.0 · Your data stays on this phone.")
    }

    func testWithoutANote() {
        XCTAssertEqual(SettingsList.aboutLine(appName: "Showcase", version: "1.0.0", note: nil), "Showcase 1.0.0")
    }
}
