import XCTest
@testable import WhitelabelKit

@MainActor
final class NavigatorTests: XCTestCase {
    private let navigator = Navigator()

    func testShowsATabRootBySelectingItAndPoppingItsStack() {
        navigator.selectedTab = "home"
        navigator.paths["catalog"] = [Destination("catalog/detail")]
        navigator.show(Destination("catalog"), tab: "catalog", inGate: false, visibleTabs: ["home", "catalog"])
        XCTAssertEqual(navigator.selectedTab, "catalog")
        XCTAssertEqual(navigator.paths["catalog"] ?? [], [])
    }

    func testPushesOntoTheOwningTab() {
        navigator.selectedTab = "home"
        navigator.show(Destination("catalog/detail"), tab: "catalog", inGate: false, visibleTabs: ["home", "catalog"])
        XCTAssertEqual(navigator.selectedTab, "catalog")
        XCTAssertEqual(navigator.paths["catalog"], [Destination("catalog/detail")])
    }

    func testPushesAGateDestinationOntoTheGateStack() {
        navigator.show(Destination("auth/reset"), tab: nil, inGate: true, visibleTabs: ["home"])
        XCTAssertEqual(navigator.gatePath, [Destination("auth/reset")])
        XCTAssertTrue(navigator.paths.isEmpty)
    }

    /** On sign-in or sign-out the next user starts clean, as Android pops the whole back stack. */
    func testResetClearsStacksAndSelection() {
        navigator.selectedTab = "catalog"
        navigator.paths["catalog"] = [Destination("catalog/detail")]
        navigator.gatePath = [Destination("auth/reset")]
        navigator.reset()
        XCTAssertNil(navigator.selectedTab)
        XCTAssertTrue(navigator.paths.isEmpty)
        XCTAssertTrue(navigator.gatePath.isEmpty)
    }
}

@MainActor
final class GateNavigatorTests: XCTestCase {
    /** While signed out, sign-in's links (register, forgot) go on the gate's stack. */
    func testPushAndPopUseTheGateStackWhileItShows() {
        let navigator = Navigator()
        navigator.selectedTab = "settings"
        navigator.isGateShowing = true
        navigator.push(Destination("auth/register"))
        XCTAssertEqual(navigator.gatePath, [Destination("auth/register")])
        navigator.pop()
        XCTAssertTrue(navigator.gatePath.isEmpty)
        XCTAssertTrue(navigator.paths.isEmpty)
    }
}

@MainActor
final class TabShortcutTests: XCTestCase {
    func testOpensOnlyAVisibleTabAtItsRoot() {
        let navigator = Navigator()
        navigator.visibleTabs = ["home", "settings"]
        navigator.selectedTab = "home"
        navigator.paths["settings"] = [Destination("settings/x")]
        XCTAssertFalse(navigator.canOpenTab("catalog"))
        navigator.openTab("catalog")
        XCTAssertEqual(navigator.selectedTab, "home")
        navigator.openTab("settings")
        XCTAssertEqual(navigator.selectedTab, "settings")
        XCTAssertEqual(navigator.paths["settings"], [])
    }
}
