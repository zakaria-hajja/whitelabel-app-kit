import XCTest
@testable import WhitelabelAds

/** Only a new banner view (a new unit or width) is a new request; the same view appearing again isn't. */
final class BannerRequestsTests: XCTestCase {

    func testTheFirstViewIsARequest() {
        var requests = BannerRequests()
        XCTAssertTrue(requests.appeared(unitId: "u", width: 390))
    }

    func testTheSameViewAppearingAgainIsNoRequest() {
        // Popping back to the tab root, or switching back to the tab, shows the same banner view.
        var requests = BannerRequests()
        _ = requests.appeared(unitId: "u", width: 390)
        XCTAssertFalse(requests.appeared(unitId: "u", width: 390))
        XCTAssertFalse(requests.appeared(unitId: "u", width: 390.4))
    }

    func testANewWidthOrUnitIsANewRequest() {
        var requests = BannerRequests()
        _ = requests.appeared(unitId: "u", width: 390)
        XCTAssertTrue(requests.appeared(unitId: "u", width: 844))
        XCTAssertTrue(requests.appeared(unitId: "v", width: 844))
        XCTAssertTrue(requests.appeared(unitId: "u", width: 390))
    }
}
