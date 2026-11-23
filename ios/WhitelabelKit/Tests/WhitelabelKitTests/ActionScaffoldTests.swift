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

    // MARK: Live: a slot's content changing while the screen shows

    /** Hosts [view] live in a 320 × 568 window of the test app's scene. */
    private func host<V: View>(_ view: V) -> UIWindow {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        let window = scene.map { UIWindow(windowScene: $0) } ?? UIWindow()
        window.frame = CGRect(x: 0, y: 0, width: 320, height: 568)
        window.rootViewController = UIHostingController(rootView: view.environment(\.appStyle, AppStyle(pack: .gallery)))
        window.isHidden = false
        window.layoutIfNeeded()
        return window
    }

    private func settle(_ condition: () -> Bool) {
        let deadline = Date().addingTimeInterval(2)
        while !condition() && Date() < deadline { RunLoop.main.run(until: Date().addingTimeInterval(0.02)) }
    }

    /**
     * A 200 pt message makes the dock too tall for 40 % of the window, so the 40 pt fine print moves
     * to the content. When the message's content shrinks or disappears (the slot itself stays, as with
     * `if let error`), the fine print must come back to the dock.
     */
    private func assertFinePrintReturns(after change: (Probe) -> Void, file: StaticString = #filePath, line: UInt = #line) {
        let probe = Probe()
        let window = host(ProbeScreen(probe: probe))
        settle { probe.finePrintInContent }
        XCTAssertTrue(probe.finePrintInContent, "the fine print should leave the dock under a 200 pt message", file: file, line: line)
        change(probe)
        settle { probe.finePrintInDock }
        XCTAssertTrue(probe.finePrintInDock, "the fine print should come back to the dock", file: file, line: line)
        window.isHidden = true
    }

    func testExtrasReturnToTheDockWhenTheMessageDisappears() {
        assertFinePrintReturns { $0.message = nil }
    }

    func testExtrasReturnToTheDockWhenTheMessageShrinks() {
        assertFinePrintReturns { $0.message = 10 }
    }

    /**
     * An empty message slot adds no gap: the space above the dock ends where it does with no message
     * at all (the dock is pinned at the bottom, so a stray gap would raise its top).
     */
    func testAnEmptyMessageAddsNoGap() {
        let empty = Probe()
        empty.message = nil
        let emptyWindow = host(ProbeScreen(probe: empty, scrolls: false))
        let none = Probe()
        let noneWindow = host(ProbeScreen(probe: none, withMessage: false, scrolls: false))
        settle { empty.content != .zero && none.content != .zero && empty.finePrintInDock && none.finePrintInDock }
        XCTAssertNotEqual(none.content, .zero)
        XCTAssertEqual(empty.content.maxY, none.content.maxY, accuracy: 0.5)
        emptyWindow.isHidden = true
        noneWindow.isHidden = true
    }

    // MARK: Live: banner, announcement, keyboard

    /** A banner (an undo, a snackbar) sits 8 above the dock, so it never covers the primary. */
    func testABannerSitsEightAboveTheDock() {
        let probe = Probe()
        let window = host(BannerScreen(probe: probe))
        settle { probe.primary != .zero && probe.banner != .zero }
        XCTAssertNotEqual(probe.banner, .zero)
        // The dock's top is its 12 top padding above the primary (no message).
        XCTAssertEqual(probe.banner.maxY, probe.primary.minY - 12 - 8, accuracy: 0.5)
        window.isHidden = true
    }

    /** VoiceOver hears a new message line (Android: the message is a polite live region). */
    func testANewMessageIsAnnounced() {
        var posted: [String] = []
        let saved = ActionScaffoldAnnouncement.post
        ActionScaffoldAnnouncement.post = { posted.append($0) }
        defer { ActionScaffoldAnnouncement.post = saved }
        let probe = Probe()
        probe.announcement = nil
        let window = host(AnnouncingScreen(probe: probe))
        settle { probe.primary != .zero }
        probe.announcement = "Couldn't save"
        settle { !posted.isEmpty }
        XCTAssertEqual(posted, ["Couldn't save"])
        probe.announcement = nil
        settle { false }
        XCTAssertEqual(posted, ["Couldn't save"], "a cleared message isn't announced")
        window.isHidden = true
    }
}

@MainActor
private final class Probe: ObservableObject {
    /** The message's height; nil renders nothing in the (still present) slot. */
    @Published var message: CGFloat? = 200
    @Published var announcement: String?
    var banner: CGRect = .zero
    var primary: CGRect = .zero
    var finePrint: CGRect = .zero
    /** The content's frame; with `scrolls: false` it fills the space above the dock. */
    var content: CGRect = .zero
    var finePrintInDock: Bool { primary != .zero && finePrint.minY >= primary.maxY }
    var finePrintInContent: Bool { primary != .zero && finePrint != .zero && finePrint.maxY <= primary.minY }
}

private struct ProbeScreen: View {
    @ObservedObject var probe: Probe
    var withMessage = true
    var scrolls = true

    var body: some View {
        if withMessage {
            ActionScaffold(scrolls: scrolls) { content } primary: { primary } message: {
                if let height = probe.message { Color.red.frame(height: height) }
            } finePrint: { finePrint }
        } else {
            ActionScaffold(scrolls: scrolls) { content } primary: { primary } finePrint: { finePrint }
        }
    }

    private var content: some View {
        Color.clear.frame(maxHeight: scrolls ? 20 : .infinity)
            .onGeometryChange(for: CGRect.self, of: { $0.frame(in: .global) }, action: { probe.content = $0 })
    }

    private var primary: some View {
        Color.blue.frame(height: 48)
            .onGeometryChange(for: CGRect.self, of: { $0.frame(in: .global) }, action: { probe.primary = $0 })
    }

    private var finePrint: some View {
        Color.green.frame(height: 40)
            .onGeometryChange(for: CGRect.self, of: { $0.frame(in: .global) }, action: { probe.finePrint = $0 })
    }
}

private struct BannerScreen: View {
    let probe: Probe

    var body: some View {
        ActionScaffold { Color.clear.frame(height: 20) } primary: {
            Color.blue.frame(height: 48)
                .onGeometryChange(for: CGRect.self, of: { $0.frame(in: .global) }, action: { probe.primary = $0 })
        } banner: {
            Color.orange.frame(height: 60)
                .onGeometryChange(for: CGRect.self, of: { $0.frame(in: .global) }, action: { probe.banner = $0 })
        }
    }
}

private struct AnnouncingScreen: View {
    @ObservedObject var probe: Probe

    var body: some View {
        ActionScaffold(messageAnnouncement: probe.announcement) { Color.clear.frame(height: 20) } primary: {
            Color.blue.frame(height: 48)
                .onGeometryChange(for: CGRect.self, of: { $0.frame(in: .global) }, action: { probe.primary = $0 })
        } message: {
            if let text = probe.announcement { Text(text) }
        }
    }
}
