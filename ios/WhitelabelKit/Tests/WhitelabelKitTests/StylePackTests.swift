import XCTest
import SwiftUI
@testable import WhitelabelKit

/** The Swift packs carry the Kotlin packs' values (core/designsystem StylePack, MotionSpec, Spacing): Kotlin wins. */
final class StylePackTests: XCTestCase {

    func testGalleryMatchesKotlin() {
        let gallery = StylePack.gallery
        XCTAssertEqual(gallery.name, "Gallery")
        XCTAssertEqual(gallery.corners, Corners(small: 4, medium: 8, large: 14))
        XCTAssertEqual(gallery.surfaces, .hairline)
        XCTAssertEqual(gallery.sizing, Sizing(controlHeight: 52, listRowMinHeight: 72, screenGutter: 20))
        XCTAssertTrue(gallery.type.titlesUseBodyFont)
        XCTAssertEqual(gallery.motion.screenEnter, .tween(milliseconds: 420, CubicBezier(0.2, 0, 0, 1)))
        XCTAssertEqual(gallery.motion.screenExit, .tween(milliseconds: 320, CubicBezier(0.3, 0, 0.8, 0.15)))
        XCTAssertEqual(gallery.motion.sharedElement, .tween(milliseconds: 420, CubicBezier(0.2, 0, 0, 1)))
        XCTAssertEqual(gallery.motion.tabSwitch, .tween(milliseconds: 210, CubicBezier(0.2, 0, 0, 1)))
        XCTAssertEqual(gallery.motion.tabSwitchScale, 0.96)
        XCTAssertEqual(gallery.motion.emphasis, .spring(dampingRatio: 0.55, stiffness: 380))
        XCTAssertEqual(gallery.motion.snackbar, .tween(milliseconds: 200, CubicBezier(0.2, 0, 0, 1)))
        XCTAssertEqual(gallery.haptics, [.confirm: .tick, .success: .confirm, .toggle: .toggle])
    }

    func testMaterialMatchesKotlin() {
        let material = StylePack.material
        XCTAssertEqual(material.name, "Material")
        XCTAssertEqual(material.corners, Corners(small: 8, medium: 12, large: 16)) // the foundation's brand_corner_* defaults
        XCTAssertEqual(material.surfaces, .shadow)
        XCTAssertEqual(material.sizing, Sizing(controlHeight: 48, listRowMinHeight: 56, screenGutter: 16))
        XCTAssertFalse(material.type.titlesUseBodyFont)
        XCTAssertEqual(material.motion.screenEnter, .tween(milliseconds: 700, CubicBezier(0.4, 0, 0.2, 1)))
        XCTAssertEqual(material.motion.emphasis, .spring(dampingRatio: 1, stiffness: 400))
        XCTAssertTrue(material.haptics.isEmpty)
    }

    func testSpacingMatchesKotlin() {
        XCTAssertEqual([Spacing.xs, Spacing.sm, Spacing.md, Spacing.lg, Spacing.xl], [4, 8, 16, 24, 32])
    }

    func testReducedMotionUsesThePacksReducedValues() {
        let fade = MotionCurve.tween(milliseconds: 150, CubicBezier(0, 0, 1, 1))
        let reduced = StylePack.gallery.reduced().motion
        XCTAssertEqual([reduced.screenEnter, reduced.screenExit, reduced.sharedElement, reduced.tabSwitch, reduced.emphasis, reduced.snackbar], Array(repeating: fade, count: 6))
        XCTAssertEqual(reduced.tabSwitchScale, 1)
    }

    func testASpringKeepsItsStiffnessAsSwiftUIResponse() {
        // SwiftUI's response is the period of the undamped spring: 2π / √stiffness, at unit mass.
        XCTAssertEqual(MotionCurve.spring(dampingRatio: 0.55, stiffness: 380).springResponse, 2 * .pi / sqrt(380), accuracy: 0.0001)
    }

    func testMissingBrandFontFallsBackToSystem() {
        let fonts = BrandFonts(displayName: "No Such Font", bodyName: "No Such Font Either")
        XCTAssertFalse(fonts.hasCustomDisplay)
        XCTAssertFalse(fonts.hasCustomBody)
    }
}
