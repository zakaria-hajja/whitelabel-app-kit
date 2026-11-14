import GoogleMobileAds
import XCTest
import Shared
@testable import WhitelabelAds

/** The banner's request: `npa=1` in Google's `Extras` with `ads.nonPersonalized`, nothing otherwise. */
final class AdRequestTests: XCTestCase {

    private func npa(_ request: Request) -> String? {
        (request.adNetworkExtras(for: Extras.self) as? Extras)?.additionalParameters?["npa"] as? String
    }

    func testANonPersonalizedRequestCarriesNpa1() {
        XCTAssertEqual(npa(GoogleBannerHost.request(policy: AdRequestPolicy(nonPersonalized: true))), "1")
    }

    func testADefaultRequestCarriesNoExtras() {
        XCTAssertNil(GoogleBannerHost.request(policy: AdRequestPolicy.companion.DEFAULT).adNetworkExtras(for: Extras.self))
    }
}
