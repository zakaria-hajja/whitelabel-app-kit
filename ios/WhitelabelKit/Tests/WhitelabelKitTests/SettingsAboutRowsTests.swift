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

    func testTheAppsRowsAreKept() {
        let options = SettingsOptions(aboutRows: { AnyView(Text("Open-source licences")) })
        XCTAssertNotNil(options.aboutRows)
    }
}
