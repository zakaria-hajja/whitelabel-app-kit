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

    /** With VoiceOver, reaching the Undo button takes longer than a glance: an action banner stays at least 10 s. */
    func testAnActionBannerStaysLongerWithVoiceOver() {
        XCTAssertEqual(ActionBanner.duration(seconds: 4, hasAction: true, voiceOver: true), 10)
        XCTAssertEqual(ActionBanner.duration(seconds: 15, hasAction: true, voiceOver: true), 15)
        XCTAssertEqual(ActionBanner.duration(seconds: 4, hasAction: true, voiceOver: false), 4)
        XCTAssertEqual(ActionBanner.duration(seconds: 4, hasAction: false, voiceOver: true), 4)
    }

    func testItemsAreEqualByIdentity() {
        let a = item("Same")
        XCTAssertEqual(a, a)
        XCTAssertNotEqual(a, item("Same"))
    }
}
