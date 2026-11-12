import XCTest
import Shared
@testable import WhitelabelAds
@testable import WhitelabelKit

@MainActor
final class AdsServiceTests: XCTestCase {

    override func setUp() { SharedGraph.shared.stop(); SharedGraph.shared.clearFirebaseServices(); AdBanners.unregister() }
    override func tearDown() { SharedGraph.shared.stop(); SharedGraph.shared.clearFirebaseServices(); AdBanners.unregister() }

    func testFollowsTheAdsSetting() {
        XCTAssertEqual(AdsService().kind, .ads)
    }

    func testOffModeInstallsNothing() {
        AdsService().install(mode: "off")
        XCTAssertNil(AdBanners.host)
    }

    func testTheAdMobAppIdMustBePresent() {
        XCTAssertEqual(
            AdsService.problem([:]),
            "Ads are on, but Info.plist has no GADApplicationIdentifier (the AdMob app ID); set GADApplicationIdentifier = $(WL_ADMOB_APP_ID) in Info.plist"
        )
        XCTAssertNotNil(AdsService.problem(["GADApplicationIdentifier": " "]))
        XCTAssertNil(AdsService.problem(["GADApplicationIdentifier": "ca-app-pub-3940256099942544~1458002511"]))
    }

    func testTrackingOnNeedsThePromptText() {
        let id = ["GADApplicationIdentifier": "ca-app-pub-3940256099942544~1458002511"]
        XCTAssertEqual(
            AdsService.problem(id.merging(["WLAdsTracking": "yes"]) { $1 }),
            "ads.ios.requestTracking is on, but Info.plist has no NSUserTrackingUsageDescription; use the WhitelabelAdsApp template, which adds it from ads.ios.trackingMessage"
        )
        XCTAssertNil(AdsService.problem(id.merging(["WLAdsTracking": "yes", "NSUserTrackingUsageDescription": "Why"]) { $1 }))
    }

    func testTheTrackingPortFollowsTheSetting() {
        XCTAssertTrue(AdsService.trackingPort(["WLAdsTracking": "yes"]) is AttTrackingPort)
        XCTAssertFalse(AdsService.trackingPort(["WLAdsTracking": "no"]) is AttTrackingPort)
        XCTAssertFalse(AdsService.trackingPort([:]) is AttTrackingPort)
    }
}
