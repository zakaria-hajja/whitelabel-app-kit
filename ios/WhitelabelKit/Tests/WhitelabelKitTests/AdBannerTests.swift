import XCTest
import Shared
@testable import WhitelabelKit

/** What the kit's banner shows for each shared banner state, and how a load settles. */
final class AdBannerTests: XCTestCase {

    func testAReadyBannerLoadsItsUnit() {
        XCTAssertEqual(AdBannerSlot(state: BannerStateReady(unitId: "u1")), .load(unitId: "u1"))
    }

    func testEveryHiddenReasonTakesNoSpace() {
        for reason in [HideReason.adsOff, .consentPending, .consentFailed, .consentNoAds, .firstLaunch, .adFree, .loading] {
            XCTAssertEqual(AdBannerSlot(state: BannerStateHidden(reason: reason)), .hidden, "\(reason)")
        }
    }

    func testAnUndeclaredPlacementIsTold() {
        // The view stops a Debug build on it and stays hidden in Release.
        XCTAssertEqual(AdBannerSlot(state: BannerStateHidden(reason: .undeclared)), .undeclared)
        XCTAssertEqual(AdBannerSlot.undeclaredMessage("x"), "placement 'x' isn't declared in ads { }")
    }

    func testOnlyALoadedAdTakesSpaceAndAFailedRefreshKeepsIt() {
        XCTAssertEqual(BannerLoad.loading.on(.loaded), .loaded)
        XCTAssertEqual(BannerLoad.loading.on(.failed), .failed)
        XCTAssertEqual(BannerLoad.failed.on(.loaded), .loaded)
        XCTAssertEqual(BannerLoad.loaded.on(.failed), .loaded)
        XCTAssertTrue(BannerLoad.loaded.takesSpace)
        XCTAssertFalse(BannerLoad.loading.takesSpace)
        XCTAssertFalse(BannerLoad.failed.takesSpace)
    }

    func testTheShellDrawsTheTabsPlacementOnly() {
        let definition = AppDefinition(style: .material, ads: [AdPlacement(name: "own", underTab: nil), AdPlacement(name: "top", underTab: "home")]) {
            StubFeature("home", tab: "home")
            StubFeature("settings", tab: "settings")
        }
        XCTAssertEqual(definition.tabPlacement(forTab: "home"), "top")
        XCTAssertNil(definition.tabPlacement(forTab: "settings"))
        XCTAssertNil(AppDefinition(style: .material) { StubFeature("home", tab: "home") }.tabPlacement(forTab: "home"))
    }

    @MainActor func testWithoutABannerHostNothingIsDrawn() {
        XCTAssertNil(AdBanners.host)
    }
}
