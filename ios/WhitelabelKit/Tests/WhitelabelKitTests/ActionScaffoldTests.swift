import XCTest
import SwiftUI
@testable import WhitelabelKit

/** The action dock's rules (Kotlin: ActionDockLayoutTest, same numbers) and the scaffold's size. */
@MainActor
final class ActionScaffoldTests: XCTestCase {

    // Top 12 + bottom 16 padding + a 48 bottom inset.
    private let chrome: CGFloat = 28 + 48
    private let gap: CGFloat = 8

    private func overflow(
        available: CGFloat, primary: CGFloat, secondary: CGFloat = 0, finePrint: CGFloat = 0,
        message: CGFloat = 0, compact: Bool = false
    ) -> DockOverflow {
        ActionDockLayout.overflow(
            available: available, chrome: chrome, gap: gap, message: message, primary: primary,
            secondary: secondary, finePrint: finePrint, maxFraction: 0.4, compact: compact
        )
    }

    func testOverflowFitsReturnsNone() {
        XCTAssertEqual(overflow(available: 2000, primary: 52, secondary: 48, finePrint: 40), .none)
    }

    func testOverflowTallFinePrintMovesFirst() {
        // Cap 320; full 76+104+8+96+8+160 = 452; without fine print 284.
        XCTAssertEqual(overflow(available: 800, primary: 104, secondary: 96, finePrint: 160), .finePrint)
    }

    func testOverflowStillTooTallMovesSecondaryToo() {
        XCTAssertEqual(overflow(available: 500, primary: 104, secondary: 96, finePrint: 60), .finePrintAndSecondary)
    }

    func testOverflowPrimaryAloneNeverOverflows() {
        XCTAssertEqual(overflow(available: 100, primary: 200), .none)
    }

    func testOverflowAbsentPartsAddNoGap() {
        XCTAssertEqual(overflow(available: 1000, primary: 300, message: 16), .none)
        XCTAssertEqual(overflow(available: 1000, primary: 280, finePrint: 36), .none)
        XCTAssertEqual(overflow(available: 1000, primary: 281, finePrint: 36), .finePrint)
    }

    func testOverflowCompactKeyboardMovesExtras() {
        XCTAssertEqual(overflow(available: 2000, primary: 52, secondary: 48, finePrint: 40, compact: true), .finePrintAndSecondary)
        XCTAssertEqual(overflow(available: 2000, primary: 52, compact: true), .none)
    }

    func testFadeShowsOnlyWhenContentBottomIsBelowViewport() {
        XCTAssertTrue(ActionDockLayout.showsFade(contentBottom: 700, viewportBottom: 600))
        XCTAssertFalse(ActionDockLayout.showsFade(contentBottom: 600, viewportBottom: 600))
        XCTAssertFalse(ActionDockLayout.showsFade(contentBottom: 600.3, viewportBottom: 600))
    }

    private let longLabel = "Continue to the next step of setting things up"

    private func render<V: View>(_ view: V, dynamicType: DynamicTypeSize) -> CGSize {
        let renderer = ImageRenderer(
            content: view
                .environment(\.dynamicTypeSize, dynamicType)
                .environment(\.appStyle, AppStyle(pack: .gallery, fonts: BrandFonts(displayName: nil, bodyName: nil)))
                .frame(width: 320)
        )
        renderer.proposedSize = ProposedViewSize(width: 320, height: 568)
        return renderer.uiImage?.size ?? .zero
    }

    /** Review Focus 1: at AX3 on a small phone the dock never pushes the scaffold past the window. */
    func testDockKeepsThePrimaryAtAX3() {
        let rows = VStack { ForEach(0..<30) { Text("Row \($0)") } }
        let plain = ActionScaffold(scrolls: false) { rows } primary: {
            Button(longLabel) {}.buttonStyle(PrimaryButtonStyle())
        }
        let extras = ActionScaffold(scrolls: false) { rows } primary: {
            Button(longLabel) {}.buttonStyle(PrimaryButtonStyle())
        } secondary: {
            Button("I don't remember") {}.buttonStyle(TextButtonStyle())
        } finePrint: {
            Text("Everything stays on this phone. Nothing is shared, ever, with anyone at all.")
        }
        let plainSize = render(plain, dynamicType: .accessibility3)
        let extrasSize = render(extras, dynamicType: .accessibility3)
        XCTAssertGreaterThan(plainSize.height, 0)
        XCTAssertLessThanOrEqual(plainSize.height, 568.5)
        XCTAssertLessThanOrEqual(extrasSize.height, plainSize.height + 0.5)
    }

    /**
     * A 560 pt fine print can't stay in the dock of a 568 pt window (cap 227): it moves into the
     * (clipped) content, so the render stays 568. Left in the dock (28 + 52 + 8 + 560 = 648), it
     * would push the render past it.
     */
    func testATallFinePrintLeavesTheDock() {
        let scaffold = ActionScaffold(scrolls: false) { Text("Short") } primary: {
            Button("Continue") {}.buttonStyle(PrimaryButtonStyle())
        } finePrint: {
            Color.clear.frame(height: 560)
        }
        XCTAssertLessThanOrEqual(render(scaffold, dynamicType: .large).height, 568.5)
    }
}
