import XCTest
@testable import WhitelabelKit

/**
 * One banner at a time, never a long chain, and a protected one (a one-tap Delete's Undo, the only way
 * back) is never knocked out. As Android's Notices in Munch.
 */
final class BannerQueueTests: XCTestCase {

    private func item(_ text: String, protect: Bool = false) -> BannerItem {
        BannerItem(text: text, actionTitle: "Undo", protect: protect) {}
    }

    func testANewerBannerReplacesAnOrdinaryOne() {
        var queue = BannerQueue()
        XCTAssertEqual(queue.offer(item("Fed Netflix"))?.text, "Fed Netflix")
        XCTAssertEqual(queue.offer(item("Fed Spotify"))?.text, "Fed Spotify")
        XCTAssertNil(queue.finished(), "the replaced one must not come back")
    }

    func testAProtectedBannerKeepsItsPlaceThenTheNewestWaitingOneShows() {
        var queue = BannerQueue()
        XCTAssertEqual(queue.offer(item("Deleted Netflix", protect: true))?.text, "Deleted Netflix")
        XCTAssertNil(queue.offer(item("Fed Spotify")))
        XCTAssertNil(queue.offer(item("Fed Disney+")))
        XCTAssertEqual(queue.finished()?.text, "Fed Disney+")
        XCTAssertNil(queue.finished(), "one waiting slot: no chain")
    }

    func testProtectedBannersQueueInOrder() {
        var queue = BannerQueue()
        _ = queue.offer(item("Deleted Netflix", protect: true))
        XCTAssertNil(queue.offer(item("Fed Spotify")))
        XCTAssertNil(queue.offer(item("Deleted Disney+", protect: true)))
        XCTAssertEqual(queue.finished()?.text, "Deleted Disney+")
        XCTAssertEqual(queue.finished()?.text, "Fed Spotify")
        XCTAssertNil(queue.finished())
    }

    func testAProtectedBannerReplacesAnOrdinaryOne() {
        var queue = BannerQueue()
        _ = queue.offer(item("Fed Spotify"))
        XCTAssertEqual(queue.offer(item("Deleted Netflix", protect: true))?.text, "Deleted Netflix")
        XCTAssertNil(queue.finished())
    }
}
