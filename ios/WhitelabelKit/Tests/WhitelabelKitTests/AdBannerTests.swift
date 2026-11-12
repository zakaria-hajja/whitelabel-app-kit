import SwiftUI
import XCTest
import Shared
@testable import WhitelabelKit

/** A gate whose screens own "auth/login". */
private struct LoginGate: Feature {
    var spec: FeatureSpec { FeatureSpec(id: "auth", flagKey: nil, tabRoute: nil, requiresSignIn: false, isGate: true, requiresBackend: true) }
    func tab() -> TabItem? { nil }
    func routes(_ route: String, _ args: [String: String]) -> AnyView? { route == "auth/login" ? AnyView(EmptyView()) : nil }
}

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

    func testANewRequestCollapsesUntilItLoads() {
        for from in [BannerLoad.loading, .loaded, .failed] {
            XCTAssertEqual(from.on(.requested), .loading, "\(from)")
        }
        // A resized banner's new view that fails stays collapsed: no labelled blank gap.
        XCTAssertEqual(BannerLoad.loaded.on(.requested).on(.failed), .failed)
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

    @MainActor func testPlacementsUnderTheGatesScreensAreNamedForKotlin() {
        let definition = AppDefinition(style: .material, ads: [AdPlacement(name: "g", underTab: "auth/login"), AdPlacement(name: "h", underTab: "home")]) {
            LoginGate()
            StubFeature("home", tab: "home")
        }
        XCTAssertEqual(AppRuntime.gateRoutes(of: definition), ["auth/login"])
        XCTAssertEqual(AppRuntime.gateRoutes(of: AppDefinition(style: .material, ads: definition.ads) { StubFeature("home", tab: "home") }), [])
    }

    func testABannerNeedsTheAdsGraphAndAHost() {
        XCTAssertFalse(AdBanner.draws(hasController: false, hasHost: true))
        XCTAssertFalse(AdBanner.draws(hasController: true, hasHost: false))
        XCTAssertTrue(AdBanner.draws(hasController: true, hasHost: true))
    }
}
