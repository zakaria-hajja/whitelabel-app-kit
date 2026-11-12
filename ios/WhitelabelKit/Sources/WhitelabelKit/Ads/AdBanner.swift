import Observation
import Shared
import SwiftUI

/**
 * Draws one banner for [AdBanner]: `AdsService` (WhitelabelAds) registers Google's. Without one (an
 * app without the ads target) every banner takes no space. [banner] loads an adaptive anchored
 * banner for the available width and reports the outcome: [onLoaded] once an ad shows, [onFailed]
 * when none could load. The Swift twin of Android's `BannerHost`.
 */
public protocol BannerHost {
    @MainActor func banner(unitId: String, onLoaded: @escaping () -> Void, onFailed: @escaping () -> Void) -> AnyView
}

/** Where the app's [BannerHost] is registered (by `AdsService` in `on` mode). */
@MainActor
public enum AdBanners {
    public private(set) static var host: (any BannerHost)?

    public static func register(_ host: any BannerHost) { self.host = host }

    /** Tests. */
    static func unregister() { host = nil }
}

/** What the banner slot does for a shared [BannerState]. */
enum AdBannerSlot: Equatable {
    /** The rules let it load this unit. */
    case load(unitId: String)
    /** Hidden by the rules (or still deciding): no space. */
    case hidden
    /** The placement isn't in `AppDefinition(ads:)`: a Debug build stops, a Release one stays hidden. */
    case undeclared

    init(state: BannerState) {
        if let ready = state as? BannerStateReady {
            self = .load(unitId: ready.unitId)
        } else if let hidden = state as? BannerStateHidden, hidden.reason == .undeclared {
            self = .undeclared
        } else {
            self = .hidden
        }
    }

    /** Android's message for an undeclared placement. */
    static func undeclaredMessage(_ placement: String) -> String { "placement '\(placement)' isn't declared in ads { }" }
}

/** What the SDK reported for the banner on screen. */
enum BannerEvent { case loaded, failed }

/** Where a ready banner is: only [loaded] takes space. */
enum BannerLoad: Equatable {
    case loading, loaded, failed

    /** The state after [event]. Once an ad shows, a failed refresh keeps it (the SDK leaves the last ad in place). */
    func on(_ event: BannerEvent) -> BannerLoad {
        if event == .loaded { return .loaded }
        return self == .loaded ? .loaded : .failed
    }

    var takesSpace: Bool { self == .loaded }
}

/**
 * Follows the shared banner state of one placement on the main actor, once [observe] runs (from the
 * view's `.task`, so the copies SwiftUI builds and drops for a `@State` initial value start nothing).
 */
@MainActor
@Observable
final class AdBannerModel {
    private(set) var slot: AdBannerSlot

    @ObservationIgnored private let flow: SkieSwiftStateFlow<BannerState>

    init(controller: AdsController, placement: String) {
        // One shared flow per placement, so this is cheap.
        flow = controller.bannerState(placement: placement)
        slot = AdBannerSlot(state: flow.value)
    }

    /** Follows the state until the calling task is cancelled (the view went away). */
    func observe() async {
        for await state in flow {
            slot = AdBannerSlot(state: state)
        }
    }
}

/**
 * The banner for [placement], labelled "Ad". It takes no space while the rules hide it, while it
 * loads and when it fails (never an error), and always in an app without ads (no controller) or
 * without a [BannerHost]. A placement missing from `AppDefinition(ads:)` stops a Debug build.
 */
public struct AdBanner: View {
    private let placement: String
    @State private var model: AdBannerModel?

    public init(placement: String) {
        self.placement = placement
        _model = State(initialValue: SharedGraph.shared.adsController().map { AdBannerModel(controller: $0, placement: placement) })
    }

    public var body: some View {
        if let model, let host = AdBanners.host {
            Group {
                switch model.slot {
                case let .load(unitId):
                    LoadedBanner(unitId: unitId, host: host).id(unitId)
                case .undeclared:
                    undeclared()
                case .hidden:
                    Color.clear.frame(height: 0)
                }
            }
            .task { await model.observe() }
        }
    }
}

extension AdBanner {
    /** As on Android: an undeclared placement is a programming error, loud in Debug, hidden in Release. */
    private func undeclared() -> AnyView {
        #if DEBUG
        fatalError(AdBannerSlot.undeclaredMessage(placement))
        #else
        AnyView(Color.clear.frame(height: 0))
        #endif
    }
}

/** A banner the rules let load: in the layout (so the SDK can load it) but zero height until an ad shows. */
private struct LoadedBanner: View {
    let unitId: String
    let host: any BannerHost
    @State private var load = BannerLoad.loading
    @SwiftUI.Environment(\.appStyle) private var style

    var body: some View {
        VStack(spacing: 2) {
            Text(KitStrings.text("Ad"))
                .font(.caption2)
                .foregroundStyle(style.colors.onSurfaceVariant)
            host.banner(
                unitId: unitId,
                onLoaded: { load = load.on(.loaded) },
                onFailed: { load = load.on(.failed) }
            )
        }
        .padding(.vertical, load.takesSpace ? 2 : 0)
        .frame(maxWidth: .infinity)
        .frame(height: load.takesSpace ? nil : 0, alignment: .top)
        .clipped()
        .accessibilityHidden(!load.takesSpace)
    }
}
