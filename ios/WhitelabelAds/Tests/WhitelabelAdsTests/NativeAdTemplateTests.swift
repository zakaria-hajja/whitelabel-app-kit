import Shared
import UIKit
import XCTest
@testable import WhitelabelAds

/** A handle that isn't a Google ad (or no longer holds one). */
private final class NoAd: NSObject, NativeAdHandle {
    func destroy() {}
}

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

    /** A handle with no ad draws nothing, so the kit's item takes no space (no empty card-height gap). */
    @MainActor func testTheHostDrawsNothingForAHandleWithoutAnAd() {
        let host = GoogleNativeAdHost(policy: AdRequestPolicy(nonPersonalized: false))
        XCTAssertNil(host.item(ad: NoAd()))
    }

    /** VoiceOver reads "Ad, <headline>" first, then the rest; missing assets are skipped. */
    @MainActor func testScreenReaderOrderStartsWithTheChipThenTheHeadline() {
        let chip = UILabel(), headline = UILabel(), advertiser = UILabel(), body = UILabel(), media = UIView(), cta = UILabel()
        let order = NativeAdTemplate.readingOrder(chip: chip, headline: headline, advertiser: advertiser, body: body, media: media, callToAction: cta)
        XCTAssertEqual(order.map(ObjectIdentifier.init), [chip, headline, advertiser, body, media, cta].map(ObjectIdentifier.init))
        let sparse = NativeAdTemplate.readingOrder(chip: chip, headline: headline, advertiser: nil, body: nil, media: media, callToAction: nil)
        XCTAssertEqual(sparse.map(ObjectIdentifier.init), [chip, headline, media].map(ObjectIdentifier.init))
    }

    /**
     * After the reading order, VoiceOver still reaches every other accessible view: the icon, and the
     * AdChoices view the SDK adds once the ad is set. Hidden and empty decorative views are left out.
     */
    @MainActor func testVoiceOverReachesEveryOtherViewAfterTheReadingOrder() {
        let root = UIView()
        let chipLabel = UILabel(), advertiser = UILabel(), headline = UILabel(), body = UILabel(), ctaText = UILabel()
        let media = UIView()
        let chip = UIView()
        chip.addSubview(chipLabel)
        let header = UIStackView(arrangedSubviews: [chip, advertiser])
        let column = UIStackView(arrangedSubviews: [headline, body])
        let icon = UIImageView()
        icon.isAccessibilityElement = true
        let cta = UIView()
        cta.addSubview(ctaText)
        let bottom = UIStackView(arrangedSubviews: [icon, cta])
        let gone = UILabel()
        gone.isHidden = true
        for view in [header, media, column, bottom, gone, UIView()] { root.addSubview(view) }
        let leading = NativeAdTemplate.readingOrder(chip: chipLabel, headline: headline, advertiser: advertiser, body: body, media: media, callToAction: ctaText)
        // The SDK adds AdChoices after the template is built; here a container holding an element.
        let adChoices = UIView()
        let adChoicesIcon = UIImageView()
        adChoicesIcon.isAccessibilityElement = true
        adChoices.addSubview(adChoicesIcon)
        root.addSubview(adChoices)
        let order = NativeAdTemplate.accessibilityOrder(in: root, leading: leading)
        XCTAssertEqual(
            order.map { ObjectIdentifier($0 as AnyObject) },
            [chipLabel, headline, advertiser, body, media, ctaText, icon, adChoices].map(ObjectIdentifier.init)
        )
    }

    /** The card computes its order when VoiceOver asks, so a view the SDK adds later is in it. */
    @MainActor func testTheCardsElementsIncludeAViewAddedLater() {
        let card = OrderedNativeAdView()
        let chipLabel = UILabel(), headline = UILabel()
        card.addSubview(headline)
        card.addSubview(chipLabel)
        card.leading = [chipLabel, headline]
        let adChoices = UIView()
        adChoices.isAccessibilityElement = true
        adChoices.accessibilityLabel = "AdChoices"
        card.addSubview(adChoices)
        let elements = (card.accessibilityElements ?? []).map { ObjectIdentifier($0 as AnyObject) }
        XCTAssertEqual(elements, [chipLabel, headline, adChoices].map(ObjectIdentifier.init))
    }

    /** The advertiser's icon is an image VoiceOver can reach, named by the advertiser (else the headline). */
    @MainActor func testTheIconIsAnAccessibleImage() {
        let icon = UIImageView()
        NativeAdTemplate.makeAccessible(icon: icon, advertiser: "Quillby Inc.", headline: "Write better")
        XCTAssertTrue(icon.isAccessibilityElement)
        XCTAssertEqual(icon.accessibilityLabel, "Quillby Inc.")
        XCTAssertTrue(icon.accessibilityTraits.contains(.image))
        let unnamed = UIImageView()
        NativeAdTemplate.makeAccessible(icon: unnamed, advertiser: "", headline: "Write better")
        XCTAssertEqual(unnamed.accessibilityLabel, "Write better")
    }
}
