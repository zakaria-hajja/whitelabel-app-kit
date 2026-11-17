import SwiftUI
import XCTest
import Shared
@testable import WhitelabelKit

private final class FakeAd: NSObject, NativeAdHandle {
    var destroyed = 0
    func destroy() { destroyed += 1 }
}

/** Records each load; the test answers it through the stored callbacks. */
@MainActor
private final class FakeHost: NativeAdHost {
    struct Load {
        let unitId: String
        let onLoaded: (NativeAdHandle) -> Void
        let onFailed: () -> Void
    }

    var loads: [Load] = []
    let itemHeight: CGFloat = 176

    func load(unitId: String, onLoaded: @escaping (NativeAdHandle) -> Void, onFailed: @escaping () -> Void) {
        loads.append(Load(unitId: unitId, onLoaded: onLoaded, onFailed: onFailed))
    }

    func item(ad: NativeAdHandle) -> AnyView { AnyView(EmptyView()) }
}

/** The kit's native ad slot: the same visit rules as Android's `NativeAdSlotDriver`. */
@MainActor
final class NativeAdTests: XCTestCase {

    private var now: Int64 = 0
    private lazy var rotation = NativeAdRotation(nowMillis: { [unowned self] in KotlinLong(value: self.now) }, refreshMillis: 60_000, maxAgeMillis: 3_600_000)
    private let host = FakeHost()
    private let center = Foundation.NotificationCenter()

    override func tearDown() {
        NativeAds.unregister()
        super.tearDown()
    }

    /** Runs a model on a stream the test feeds; returns the model, the feed and the running task. */
    private func running() -> (NativeAdSlotModel, AsyncStream<AdBannerSlot>.Continuation, Task<Void, Never>) {
        let model = NativeAdSlotModel(placement: "list")
        let (stream, feed) = AsyncStream<AdBannerSlot>.makeStream()
        let task = Task { await model.run(slots: stream, host: host, rotation: rotation, center: center) }
        return (model, feed, task)
    }

    /** Lets the model's task take what was fed. */
    private func settle() async {
        for _ in 0..<20 { await Task.yield() }
    }

    func testNoControllerNeverLoads() async throws {
        // The test graph is an app without ads: no controller.
        try TestGraph.start()
        defer { TestGraph.stop() }
        NativeAds.register(host)
        XCTAssertNil(SharedGraph.shared.adsController())
        let model = NativeAdSlotModel(placement: "list")
        await model.observe()
        XCTAssertTrue(host.loads.isEmpty)
        XCTAssertFalse(model.isLoaded)
        XCTAssertFalse(NativeAdSlotModel.loads(hasController: false, hasHost: true))
    }

    func testNoHostNeverLoads() async throws {
        try TestGraph.start()
        defer { TestGraph.stop() }
        NativeAds.unregister()
        let model = NativeAdSlotModel(placement: "list")
        await model.observe()
        XCTAssertFalse(model.isLoaded)
        XCTAssertFalse(NativeAdSlotModel.loads(hasController: true, hasHost: false))
        XCTAssertTrue(NativeAdSlotModel.loads(hasController: true, hasHost: true))
    }

    func testReadyRequestsOnceAndShowsLoadedAd() async {
        let (model, feed, task) = running()
        feed.yield(.load(unitId: "u"))
        await settle()
        feed.yield(.load(unitId: "u"))
        await settle()
        XCTAssertEqual(host.loads.map(\.unitId), ["u"])
        XCTAssertFalse(model.isLoaded)
        let ad = FakeAd()
        host.loads[0].onLoaded(ad)
        XCTAssertTrue(model.ad === ad)
        feed.finish()
        await task.value
    }

    func testHiddenRemovesAdWithoutDestroying() async {
        let (model, feed, task) = running()
        feed.yield(.load(unitId: "u"))
        await settle()
        let ad = FakeAd()
        host.loads[0].onLoaded(ad)
        feed.yield(.hidden)
        await settle()
        XCTAssertNil(model.ad)
        XCTAssertEqual(ad.destroyed, 0)
        now = 30_000
        feed.yield(.load(unitId: "u"))
        await settle()
        XCTAssertTrue(model.ad === ad)
        XCTAssertEqual(host.loads.count, 1)
        feed.finish()
        await task.value
    }

    func testLoadAfterCancelIsNotShown() async {
        let (model, feed, task) = running()
        feed.yield(.load(unitId: "u"))
        await settle()
        task.cancel()
        await task.value
        let ad = FakeAd()
        host.loads[0].onLoaded(ad)
        XCTAssertNil(model.ad)
        XCTAssertEqual(ad.destroyed, 0)
        // Kept for the next visit: a new slot shows it at once, without a request.
        let (next, nextFeed, nextTask) = running()
        nextFeed.yield(.load(unitId: "u"))
        await settle()
        XCTAssertTrue(next.ad === ad)
        XCTAssertEqual(host.loads.count, 1)
        nextFeed.finish()
        await nextTask.value
    }

    func testBackgroundKeepsTheAdAndComingBackPastAnHourDropsIt() async {
        let (model, feed, task) = running()
        feed.yield(.load(unitId: "u"))
        await settle()
        let ad = FakeAd()
        host.loads[0].onLoaded(ad)
        center.post(name: UIApplication.didEnterBackgroundNotification, object: nil)
        XCTAssertTrue(model.ad === ad)
        now = 3_600_001
        center.post(name: UIApplication.willEnterForegroundNotification, object: nil)
        XCTAssertNil(model.ad)
        XCTAssertEqual(ad.destroyed, 1)
        XCTAssertEqual(host.loads.count, 2)
        feed.finish()
        await task.value
    }

    func testANativePlacementKeepsItsKindFromSwift() {
        XCTAssertEqual(AdPlacement(name: "list", underTab: nil, kind: .native).kind, .native)
        XCTAssertEqual(AdPlacement(name: "home", underTab: nil).kind, .banner)
    }
}
