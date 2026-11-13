import XCTest
@testable import WhitelabelAds

private struct Boom: Swift.Error {}

/** The SDK starts once for every banner, and a start that failed is tried again by the next one. */
@MainActor
final class StartOnceTests: XCTestCase {

    func testStartsOnceForEveryCaller() async {
        let once = StartOnce()
        var starts = 0
        let first = await once.run { starts += 1 }
        let second = await once.run { starts += 1 }
        XCTAssertTrue(first)
        XCTAssertTrue(second)
        XCTAssertEqual(starts, 1)
    }

    func testAFailedStartIsForgottenAndRetried() async {
        let once = StartOnce()
        let failed = await once.run { throw Boom() }
        var retried = false
        let ok = await once.run { retried = true }
        XCTAssertFalse(failed)
        XCTAssertTrue(ok)
        XCTAssertTrue(retried)
    }

    func testConcurrentCallersShareOneStart() async {
        let once = StartOnce()
        let counter = Counter()
        async let a = once.run { await counter.bump(); try await Task.sleep(nanoseconds: 50_000_000) }
        async let b = once.run { await counter.bump() }
        let results = await [a, b]
        XCTAssertEqual(results, [true, true])
        let count = await counter.value
        XCTAssertEqual(count, 1)
    }
}

private actor Counter {
    var value = 0
    func bump() { value += 1 }
}
