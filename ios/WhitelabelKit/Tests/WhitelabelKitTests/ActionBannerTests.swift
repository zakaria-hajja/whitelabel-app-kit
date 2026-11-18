import XCTest
@testable import WhitelabelKit

/** A banner with one action (Undo): shown for its time, then hidden; a newer one takes its place without a gap. */
@MainActor
final class ActionBannerTests: XCTestCase {

    private func item(_ text: String) -> BannerItem { BannerItem(text: text, actionTitle: "Undo") {} }

    func testHidesAfterSeconds() async {
        var shown: [String?] = []
        await ActionBanner.present(item("Fed · +20 pts"), show: { shown.append($0?.text) }, seconds: 0.01)
        XCTAssertEqual(shown, ["Fed · +20 pts", nil])
    }

    func testNewerItemReplacesWithoutHidingIt() async {
        var shown: [String?] = []
        let first = Task { await ActionBanner.present(self.item("First"), show: { shown.append($0?.text) }, seconds: 5) }
        try? await Task.sleep(nanoseconds: 50_000_000)
        first.cancel()
        await first.value
        await ActionBanner.present(item("Second"), show: { shown.append($0?.text) }, seconds: 0.01)
        XCTAssertEqual(shown, ["First", "Second", nil])
    }

    /**
     * As Android's snackbars: with an action (Undo) a banner stays Material's Long, 10 s, with or
     * without VoiceOver (so no VoiceOver switch is needed); without one, the short 4 s. A longer debug time (-messageSeconds) still wins.
     */
    func testAnActionBannerStaysMaterialsLong() {
        XCTAssertEqual(ActionBanner.duration(seconds: 4, hasAction: true), 10)
        XCTAssertEqual(ActionBanner.duration(seconds: 15, hasAction: true), 15)
        XCTAssertEqual(ActionBanner.duration(seconds: 4, hasAction: false), 4)
    }

    func testItemsAreEqualByIdentity() {
        let a = item("Same")
        XCTAssertEqual(a, a)
        XCTAssertNotEqual(a, item("Same"))
    }
}
