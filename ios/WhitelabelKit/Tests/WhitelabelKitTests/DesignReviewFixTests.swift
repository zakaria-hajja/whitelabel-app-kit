import XCTest
import SwiftUI
@testable import WhitelabelKit

final class TypeScaleTests: XCTestCase {
    /** Gallery's scale comes from Munch's tokens.json (the Kotlin pack defines no sizes). */
    func testGalleryFollowsTheDesignTokens() {
        let scale = TypeScale.for(.gallery)
        XCTAssertEqual(scale.display, TypeRole(size: 52, weight: .regular))
        XCTAssertEqual(scale.headline, TypeRole(size: 26, weight: .regular))
        XCTAssertEqual(scale.title, TypeRole(size: 18, weight: .semibold))
        XCTAssertEqual(scale.body, TypeRole(size: 16, weight: .regular))
        XCTAssertEqual(scale.label, TypeRole(size: 14, weight: .semibold))
    }

    /** Material keeps iOS's own sizes and Material 3's regular display weights. */
    func testMaterialUsesTheSystemScale() {
        let scale = TypeScale.for(.material)
        XCTAssertEqual(scale.display, TypeRole(size: 34, weight: .regular))
        XCTAssertEqual(scale.body, TypeRole(size: 17, weight: .regular))
    }
}

final class HapticsTests: XCTestCase {
    /** As on Android, screens play feedback themselves; each pack pattern maps to one iOS feedback. */
    func testGalleryMapsEachEventToItsPattern() {
        XCTAssertEqual(Haptics(pack: .gallery).feedback(for: .confirm), .selection)
        XCTAssertEqual(Haptics(pack: .gallery).feedback(for: .success), .success)
        XCTAssertEqual(Haptics(pack: .gallery).feedback(for: .toggle), .impact(weight: .light))
    }

    func testMaterialPlaysNothing() {
        XCTAssertNil(Haptics(pack: .material).feedback(for: .confirm))
    }
}

final class ShapeTests: XCTestCase {
    func testGalleryButtonsUseTheSmallRadiusAndMaterialButtonsAreCapsules() {
        XCTAssertEqual(StylePack.gallery.buttonShape, .rounded(4))
        XCTAssertEqual(StylePack.material.buttonShape, .capsule)
    }
}
