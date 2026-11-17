import XCTest
@testable import WhitelabelAds

final class NativeAdTemplateTests: XCTestCase {

    func testPolicyMinimums() {
        XCTAssertGreaterThanOrEqual(NativeAdTemplate.mediaSize, 120)
        XCTAssertGreaterThanOrEqual(NativeAdTemplate.badgeMinSize, 15)
        XCTAssertGreaterThanOrEqual(NativeAdTemplate.height, NativeAdTemplate.mediaSize + NativeAdTemplate.adChoicesInset)
    }

    // The same literals are pinned by Android's NativeAdTemplateTest.
    func testHeightAtFontScaleOneIsTheDesigned184() {
        XCTAssertEqual(NativeAdTemplate.height, 184)
        XCTAssertEqual(NativeAdTemplate.height(fontScale: 1), 184)
    }

    func testSmallerFontScalesKeepTheDesignedHeight() {
        XCTAssertEqual(NativeAdTemplate.height(fontScale: 0.85), 184)
    }

    func testLargerFontScalesGrowTheHeightByTheSpecsFormula() {
        XCTAssertEqual(NativeAdTemplate.height(fontScale: 1.15), 198)
        XCTAssertEqual(NativeAdTemplate.height(fontScale: 1.3), 212)
        XCTAssertEqual(NativeAdTemplate.height(fontScale: 1.5), 230)
        XCTAssertEqual(NativeAdTemplate.height(fontScale: 1.8), 263)
        XCTAssertEqual(NativeAdTemplate.height(fontScale: 2), 284)
    }

    func testAnAdWithoutAHeadlineOrMediaIsNotShown() {
        XCTAssertTrue(NativeAdTemplate.isRenderable(headline: "Quillby", hasMedia: true))
        XCTAssertFalse(NativeAdTemplate.isRenderable(headline: nil, hasMedia: true))
        XCTAssertFalse(NativeAdTemplate.isRenderable(headline: "", hasMedia: true))
        XCTAssertFalse(NativeAdTemplate.isRenderable(headline: "Quillby", hasMedia: false))
    }
}
