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

    var draws = true

    func item(ad: NativeAdHandle) -> AnyView? { draws ? AnyView(EmptyView()) : nil }
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
    private func running() -> (NativeAdSlotModel, AsyncStream<BannerState>.Continuation, Task<Void, Never>) {
        let model = NativeAdSlotModel(placement: "list")
        let (stream, feed) = AsyncStream<BannerState>.makeStream()
        let task = Task { await model.run(states: stream, host: host, rotation: rotation, center: center) }
        return (model, feed, task)
    }

    /** The shared rules' states the tests feed. */
    private let ready = BannerStateReady(unitId: "u")
    private let appHidden = BannerStateHidden(reason: .appHidden)

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
        feed.yield(ready)
        await settle()
        feed.yield(ready)
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
        feed.yield(ready)
        await settle()
        let ad = FakeAd()
        host.loads[0].onLoaded(ad)
        feed.yield(appHidden)
        await settle()
        XCTAssertNil(model.ad)
        XCTAssertEqual(ad.destroyed, 0)
        now = 30_000
        feed.yield(ready)
        await settle()
        XCTAssertTrue(model.ad === ad)
        XCTAssertEqual(host.loads.count, 1)
        feed.finish()
        await task.value
    }

    func testLoadAfterCancelIsNotShown() async {
        let (model, feed, task) = running()
        feed.yield(ready)
        await settle()
        task.cancel()
        await task.value
        let ad = FakeAd()
        host.loads[0].onLoaded(ad)
        XCTAssertNil(model.ad)
        XCTAssertEqual(ad.destroyed, 0)
        // Kept for the next visit: a new slot shows it at once, without a request.
        let (next, nextFeed, nextTask) = running()
        nextFeed.yield(ready)
        await settle()
        XCTAssertTrue(next.ad === ad)
        XCTAssertEqual(host.loads.count, 1)
        nextFeed.finish()
        await nextTask.value
    }

    /** The model outlives its task (a pushed screen, a tab flip): coming back draws the held ad on the first frame. */
    func testLeavingKeepsTheHeldAdForTheReturningSlot() async {
        let (model, feed, task) = running()
        feed.yield(ready)
        await settle()
        let ad = FakeAd()
        host.loads[0].onLoaded(ad)
        task.cancel()
        await task.value
        XCTAssertTrue(model.ad === ad)
        XCTAssertEqual(ad.destroyed, 0)
        // Back within the refresh interval: the same ad, no request.
        now = 10_000
        let (stream, nextFeed) = AsyncStream<BannerState>.makeStream()
        let next = Task { await model.run(states: stream, host: host, rotation: rotation, center: center) }
        XCTAssertTrue(model.ad === ad)
        nextFeed.yield(ready)
        await settle()
        XCTAssertTrue(model.ad === ad)
        XCTAssertEqual(host.loads.count, 1)
        nextFeed.finish()
        await next.value
    }

    func testLeavingWhileHiddenLeavesNothing() async {
        let (model, feed, task) = running()
        feed.yield(ready)
        await settle()
        host.loads[0].onLoaded(FakeAd())
        feed.yield(appHidden)
        await settle()
        task.cancel()
        await task.value
        XCTAssertNil(model.ad)
    }

    /** A new model of a placement with a held ad draws it before its first state arrives. */
    func testSeedShowsTheHeldAdOnlyWhileTheRulesAreReady() async {
        let (_, feed, task) = running()
        feed.yield(ready)
        await settle()
        let ad = FakeAd()
        host.loads[0].onLoaded(ad)
        feed.finish()
        await task.value
        let fresh = NativeAdSlotModel(placement: "list")
        fresh.seed(slot: .load(unitId: "u"), rotation: rotation)
        XCTAssertTrue(fresh.ad === ad)
        fresh.seed(slot: .hidden, rotation: rotation)
        XCTAssertNil(fresh.ad)
        // Past the age limit: nothing, and peeking destroys nothing.
        now = 3_600_001
        fresh.seed(slot: .load(unitId: "u"), rotation: rotation)
        XCTAssertNil(fresh.ad)
        XCTAssertEqual(ad.destroyed, 0)
    }

    func testHiddenForAdFreeOrConsentDestroysTheHeldAd() async {
        for reason in [HideReason.adFree, .consentNoAds, .consentFailed, .consentPending] {
            let (model, feed, task) = running()
            feed.yield(ready)
            await settle()
            let ad = FakeAd()
            host.loads.last!.onLoaded(ad)
            feed.yield(BannerStateHidden(reason: reason))
            await settle()
            XCTAssertNil(model.ad, "\(reason)")
            XCTAssertEqual(ad.destroyed, 1, "\(reason)")
            feed.finish()
            await task.value
            now += 60_000
        }
    }

    /** A request a consent hide discarded neither blocks the next visit's request nor fills its slot. */
    func testARequestDiscardedByAConsentHideNeitherBlocksNorFillsTheNextVisit() async {
        let (model, feed, task) = running()
        feed.yield(ready)
        await settle()
        feed.yield(BannerStateHidden(reason: .consentNoAds))
        await settle()
        now = 60_000
        feed.yield(ready)
        await settle()
        XCTAssertEqual(host.loads.count, 2)
        let stale = FakeAd()
        host.loads[0].onLoaded(stale)
        XCTAssertEqual(stale.destroyed, 1)
        XCTAssertNil(model.ad)
        let fresh = FakeAd()
        host.loads[1].onLoaded(fresh)
        XCTAssertTrue(model.ad === fresh)
        feed.finish()
        await task.value
    }

    func testBackgroundKeepsTheAdAndComingBackPastAnHourDropsIt() async {
        let (model, feed, task) = running()
        feed.yield(ready)
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

    /** A destroyed handle (the host draws nothing) leaves no row, not an empty card-height frame. */
    func testTheItemTakesNoSpaceWhenTheHostDrawsNothing() {
        XCTAssertNotNil(NativeAdItem.row(ad: FakeAd(), host: host))
        host.draws = false
        XCTAssertNil(NativeAdItem.row(ad: FakeAd(), host: host))
        XCTAssertNil(NativeAdItem.row(ad: nil, host: host))
        XCTAssertNil(NativeAdItem.row(ad: FakeAd(), host: nil))
    }

    func testANativePlacementKeepsItsKindFromSwift() {
        XCTAssertEqual(AdPlacement(name: "list", underTab: nil, kind: .native).kind, .native)
        XCTAssertEqual(AdPlacement(name: "home", underTab: nil).kind, .banner)
    }
}
