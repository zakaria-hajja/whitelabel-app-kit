import XCTest
import SwiftUI
@testable import WhitelabelKit

@MainActor
final class ComponentTests: XCTestCase {

    private let longLabel = "Save my changes to the profile and go back to the previous screen"

    private func size<V: View>(_ view: V, width: CGFloat, dynamicType: DynamicTypeSize, pack: StylePack) -> CGSize {
        let renderer = ImageRenderer(
            content: view
                .environment(\.dynamicTypeSize, dynamicType)
                .environment(\.appStyle, AppStyle(pack: pack, fonts: BrandFonts(displayName: nil, bodyName: nil)))
                .frame(width: width)
        )
        renderer.proposedSize = ProposedViewSize(width: width, height: nil)
        return renderer.uiImage?.size ?? .zero
    }

    /** Review Focus 4: at the largest text sizes a button wraps its label and grows, never truncates or overflows. */
    func testPrimaryButtonWrapsAtAccessibility3() {
        for pack in [StylePack.material, .gallery] {
            let button = Button(longLabel) {}.buttonStyle(PrimaryButtonStyle())
            let regular = size(button, width: 320, dynamicType: .large, pack: pack)
            let huge = size(button, width: 320, dynamicType: .accessibility3, pack: pack)
            XCTAssertLessThanOrEqual(huge.width, 320, pack.name)
            XCTAssertGreaterThan(huge.height, regular.height * 2, "\(pack.name): the label should wrap onto more lines")
        }
    }

    func testAButtonIsAtLeastThePacksControlHeight() {
        for pack in [StylePack.material, .gallery] {
            let height = size(Button("OK") {}.buttonStyle(PrimaryButtonStyle()), width: 320, dynamicType: .large, pack: pack).height
            XCTAssertGreaterThanOrEqual(height, pack.sizing.controlHeight, pack.name)
        }
    }
}
