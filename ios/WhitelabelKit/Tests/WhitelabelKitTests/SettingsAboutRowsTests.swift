import XCTest
import SwiftUI
@testable import WhitelabelKit

/** An app's About rows (e.g. open-source licences) come under Terms, as on Android; without them About is unchanged. */
final class SettingsAboutRowsTests: XCTestCase {

    func testAppRowsComeUnderTerms() {
        XCTAssertEqual(SettingsList.aboutLinks(hasSupport: true, hasAppRows: true), [.support, .privacy, .terms, .appRows])
        XCTAssertEqual(SettingsList.aboutLinks(hasSupport: false, hasAppRows: true), [.privacy, .terms, .appRows])
    }

    func testWithoutAppRowsAboutIsUnchanged() {
        XCTAssertEqual(SettingsList.aboutLinks(hasSupport: true, hasAppRows: false), [.support, .privacy, .terms])
        XCTAssertEqual(SettingsList.aboutLinks(hasSupport: false, hasAppRows: false), [.privacy, .terms])
    }

    func testTheDefaultHasNoAppRows() {
        XCTAssertNil(SettingsOptions().aboutRows)
    }

    /** Rows are a view builder, as in SettingsSection: no AnyView at the call site, several rows allowed. */
    func testTheAppsRowsAreKept() {
        let options = SettingsOptions(aboutNote: "Your data stays on this phone.") {
            SettingsLinkRow("Open-source licences") {}
            SettingsLinkRow("Credits") {}
        }
        XCTAssertNotNil(options.aboutRows)
        XCTAssertEqual(options.aboutNote, "Your data stays on this phone.")
    }

    func testALinkRowRunsItsAction() {
        var taps = 0
        let row = SettingsLinkRow("Open-source licences") { taps += 1 }
        row.action()
        XCTAssertEqual(taps, 1)
        XCTAssertEqual(row.label, "Open-source licences")
    }
}
