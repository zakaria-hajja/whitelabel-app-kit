import Observation
import Shared
import SwiftUI
import UIKit
import XCTest
@testable import WhitelabelKit

/**
 * A banner belongs to its screen: switching tabs (or pushing a screen) hides it, and coming back
 * doesn't show it again. A TabView keeps the screen's state while it's away, as a push does.
 */
@MainActor
final class BannerLeaveTests: XCTestCase {

    @Observable
    @MainActor
    final class Harness {
        var tab = 0
        var item: BannerItem?
        var message: UiText?
        var shown = 0
    }

    private struct Tabs: View {
        @Bindable var model: Harness

        var body: some View {
            TabView(selection: $model.tab) {
                Color.clear
                    .actionBanner($model.item)
                    .messageBanner(model.message) {
                        model.shown += 1
                        model.message = nil
                    }
                    .tag(0)
                    .tabItem { Text(verbatim: "Banner") }
                Color.clear.tag(1).tabItem { Text(verbatim: "Other") }
            }
        }
    }

    private var window: UIWindow?

    override func tearDown() async throws {
        window?.isHidden = true
        window = nil
        try await super.tearDown()
    }

    private func host(_ model: Harness) throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.rootViewController = UIHostingController(rootView: Tabs(model: model))
        window.isHidden = false
        self.window = window
    }

    /** Polls until `condition` holds or the deadline passes (a loaded simulator can be slow). */
    private func wait(until condition: () -> Bool, timeout: TimeInterval = 5) async -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() {
            if Date() > deadline { return false }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
        return true
    }

    /** For what can't be polled (a callback that must NOT run, a view appearing): a short pause. */
    private func settle() async {
        try? await Task.sleep(nanoseconds: 300_000_000)
    }

    func testLeavingTheScreenUsesUpItsActionBanner() async throws {
        let model = Harness()
        try host(model)
        await settle()
        model.item = BannerItem(text: "Fed · +20 pts", actionTitle: "Undo") {}
        await settle()

        model.tab = 1
        let usedUp = await wait { model.item == nil }
        XCTAssertTrue(usedUp, "the banner stays pending while its screen is away")

        model.tab = 0
        await settle()
        XCTAssertNil(model.item, "the banner came back with its screen")
    }

    func testLeavingTheScreenReportsItsMessageShown() async throws {
        let model = Harness()
        try host(model)
        await settle()
        model.message = DataErrorTextKt.toUiText(DataErrorNetwork.noInternet.toKotlinEnum())
        await settle()

        model.tab = 1
        let reported = await wait { model.shown == 1 }
        XCTAssertTrue(reported, "a message seen before leaving must not show again on return")
        XCTAssertNil(model.message)
    }

    /** A one-tap Delete's Undo keeps its place: a later banner waits, and the screen's item stays the one shown. */
    func testAProtectedBannerIsNotKnockedOut() async throws {
        let model = Harness()
        try host(model)
        await settle()
        let deleted = BannerItem(text: "Deleted Netflix", actionTitle: "Undo", protect: true) {}
        model.item = deleted
        await settle()
        model.item = BannerItem(text: "Fed Spotify", actionTitle: "Undo") {}
        let kept = await wait { model.item == deleted }
        XCTAssertTrue(kept, "a later banner knocked out the protected one")
    }

    /** A message that arrives while its screen is away still shows when it comes back. */
    func testAMessageForAScreenAwayWaitsForIt() async throws {
        let model = Harness()
        try host(model)
        model.tab = 1
        await settle()
        model.message = DataErrorTextKt.toUiText(DataErrorNetwork.noInternet.toKotlinEnum())
        await settle()
        XCTAssertEqual(model.shown, 0)
        XCTAssertNotNil(model.message)
    }
}
