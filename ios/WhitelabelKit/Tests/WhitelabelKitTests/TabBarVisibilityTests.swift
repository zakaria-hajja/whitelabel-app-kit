import XCTest
@testable import WhitelabelKit

/** The tab bar shows only with two or more visible tabs, as Android's bottom bar. */
final class TabBarVisibilityTests: XCTestCase {
    func testOneVisibleTabHidesTheTabBar() {
        XCTAssertFalse(TabsView.showsTabBar(visibleTabs: 1))
    }

    func testTwoVisibleTabsShowTheTabBar() {
        XCTAssertTrue(TabsView.showsTabBar(visibleTabs: 2))
    }

    func testNoVisibleTabsHideTheTabBar() {
        XCTAssertFalse(TabsView.showsTabBar(visibleTabs: 0))
    }
}
