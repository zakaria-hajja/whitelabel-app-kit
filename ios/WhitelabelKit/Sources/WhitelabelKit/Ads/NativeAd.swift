import Observation
import Shared
import SwiftUI
import UIKit

/**
 * Loads and draws native ads for [NativeAdSlotModel] / [NativeAdItem]: `AdsService` (WhitelabelAds)
 * registers Google's. Without one (an app without the ads target) a native placement never loads.
 * [load] calls exactly one of its callbacks, on the main thread. [item] draws the fixed list-item
 * template at [itemHeight]. The Swift twin of Android's `NativeAdHost`.
 */
public protocol NativeAdHost {
    @MainActor func load(unitId: String, onLoaded: @escaping (NativeAdHandle) -> Void, onFailed: @escaping () -> Void)
    @MainActor func item(ad: NativeAdHandle) -> AnyView
    var itemHeight: CGFloat { get }
}

/** Where the app's [NativeAdHost] is registered (by `AdsService` in `on` mode). */
@MainActor
public enum NativeAds {
    public private(set) static var host: (any NativeAdHost)?

    public static func register(_ host: any NativeAdHost) { self.host = host }

    /** Tests. */
    static func unregister() { host = nil }
}

/**
 * The logic of one native ad slot, apart from SwiftUI (Android's `NativeAdSlotDriver`, the same
 * rules). It drives the placement's [rotation] and asks [load] for an ad when a visit says so;
 * [onShow] gets the ad the slot draws (nil for nothing).
 * - [ready]: the rules are `Ready` and the app is in the foreground: begins a visit, if none, and
 *   requests when the visit says so.
 * - [hidden]: the rules hide the placement: nothing shown, the visit ends, held ads stay.
 * - [paused]: the app went to the background: the visit ends but the screen keeps its ad, so coming
 *   back shows the same ad without a gap, or drops it when it is past the age limit.
 * - [dispose]: the view went away: the visit ends; a later load goes to the rotation only.
 */
@MainActor
final class NativeAdSlotDriver {
    typealias Load = (_ unitId: String, _ onLoaded: @escaping (NativeAdHandle) -> Void, _ onFailed: @escaping () -> Void) -> Void

    private let rotation: NativeAdRotation
    private let load: Load
    private let onShow: (NativeAdHandle?) -> Void
    private(set) var shown: NativeAdHandle? {
        didSet { onShow(shown) }
    }
    private var visiting = false
    private var disposed = false

    init(rotation: NativeAdRotation, load: @escaping Load, onShow: @escaping (NativeAdHandle?) -> Void = { _ in }) {
        self.rotation = rotation
        self.load = load
        self.onShow = onShow
    }

    func ready(unitId: String) {
        guard !disposed, !visiting else { return }
        visiting = true
        let visit = rotation.beginVisit()
        shown = visit.ad
        if visit.request {
            load(unitId, { [weak self, rotation] ad in
                let show = rotation.loaded(ad: ad)
                guard let self, let show, self.visiting, !self.disposed else { return }
                self.shown = show
            }, { [rotation] in rotation.failed() })
        }
    }

    func hidden() {
        endVisit()
        shown = nil
    }

    func paused() { endVisit() }

    func dispose() {
        endVisit()
        disposed = true
        shown = nil
    }

    private func endVisit() {
        guard visiting else { return }
        visiting = false
        rotation.endVisit()
    }
}

/**
 * A native placement on one screen (declared with `AdPlacement(name:underTab:kind: .native)`). Keep it in
 * `@State`, run [observe] from the screen's `.task`, and show [NativeAdItem] as a list row while
 * [isLoaded], so nothing takes space before an ad has loaded (a failed load stays silent). The rules
 * are a banner's (`AdsController.bannerState`); when to request, show and refresh is the shared
 * `NativeAdRotation`'s. Without a controller (no ads) or a [NativeAdHost] it never loads. Nothing
 * starts in `init` (SwiftUI builds and drops `@State` initial values). A placement missing from
 * `AppDefinition(ads:)` stops a Debug build.
 */
@MainActor
@Observable
public final class NativeAdSlotModel {
    public private(set) var ad: NativeAdHandle?
    public var isLoaded: Bool { ad != nil }

    @ObservationIgnored private let placement: String

    public init(placement: String) {
        self.placement = placement
    }

    /** A slot loads only in an app with ads (a controller) and a registered [NativeAdHost]. */
    static func loads(hasController: Bool, hasHost: Bool) -> Bool { hasController && hasHost }

    /** Follows the placement's rules until the calling task is cancelled (the view went away). */
    public func observe() async {
        let controller = SharedGraph.shared.adsController()
        guard Self.loads(hasController: controller != nil, hasHost: NativeAds.host != nil),
              let controller, let host = NativeAds.host else { return }
        // One shared flow per placement; its states are pumped into a stream [run] follows.
        let flow = controller.bannerState(placement: placement)
        let (slots, feed) = AsyncStream<AdBannerSlot>.makeStream()
        let pump = Task {
            for await state in flow { feed.yield(AdBannerSlot(state: state)) }
            feed.finish()
        }
        // Cancelling this task ends [run]'s loop; the pump then stops too.
        await run(slots: slots, host: host, rotation: NativeAdRotations.shared.of(placement: placement))
        pump.cancel()
    }

    /** [observe] with its inputs handed in (tests). [center] posts the app's foreground and background. */
    func run(
        slots: AsyncStream<AdBannerSlot>,
        host: any NativeAdHost,
        rotation: NativeAdRotation,
        // Qualified: an app's Shared framework may export its own `NotificationCenter`.
        center: Foundation.NotificationCenter = .default
    ) async {
        let driver = NativeAdSlotDriver(
            rotation: rotation,
            load: { host.load(unitId: $0, onLoaded: $1, onFailed: $2) },
            onShow: { [weak self] in self?.ad = $0 }
        )
        var unitId: String?
        var foreground = true
        func apply() {
            if let unitId {
                if foreground { driver.ready(unitId: unitId) } else { driver.paused() }
            } else {
                driver.hidden()
            }
        }
        // queue nil: the block runs synchronously on the posting thread, which UIKit makes the main one.
        let observers = [
            center.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: nil) { _ in
                MainActor.assumeIsolated {
                    foreground = false
                    apply()
                }
            },
            center.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: nil) { _ in
                MainActor.assumeIsolated {
                    foreground = true
                    apply()
                }
            },
        ]
        for await slot in slots {
            switch slot {
            case let .load(unit): unitId = unit
            case .hidden: unitId = nil
            case .undeclared:
                #if DEBUG
                fatalError(AdBannerSlot.undeclaredMessage(placement))
                #else
                unitId = nil
                #endif
            }
            apply()
        }
        observers.forEach(center.removeObserver)
        driver.dispose()
    }
}

/** The native ad of [slot] in the fixed template, or nothing (no space) until one has loaded. */
public struct NativeAdItem: View {
    private let slot: NativeAdSlotModel

    public init(slot: NativeAdSlotModel) {
        self.slot = slot
    }

    public var body: some View {
        if let ad = slot.ad, let host = NativeAds.host {
            host.item(ad: ad).frame(height: host.itemHeight)
        }
    }
}
