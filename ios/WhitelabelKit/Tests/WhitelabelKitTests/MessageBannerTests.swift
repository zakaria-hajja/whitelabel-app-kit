import XCTest
@testable import WhitelabelKit

@MainActor
final class MessageBannerTests: XCTestCase {

    func testReportsTheMessageShownAfterItsTime() async {
        var shown = 0
        var texts: [String?] = []
        await MessageBanner.present(resolve: { "Saved" }, show: { texts.append($0) }, seconds: 0.01) { shown += 1 }
        XCTAssertEqual(texts, ["Saved", nil])
        XCTAssertEqual(shown, 1)
    }

    /** A newer message cancels the banner: the cancelled one must not report "shown", or it would clear the newer one. */
    func testACancelledBannerDoesNotReportShown() async {
        var shown = 0
        let task = Task { await MessageBanner.present(resolve: { "First" }, show: { _ in }, seconds: 5) { shown += 1 } }
        try? await Task.sleep(nanoseconds: 50_000_000)
        task.cancel()
        await task.value
        XCTAssertEqual(shown, 0)
    }
}
