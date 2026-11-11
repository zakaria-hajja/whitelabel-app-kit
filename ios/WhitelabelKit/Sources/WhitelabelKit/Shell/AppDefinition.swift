import SwiftUI
import Shared

/**
 * Everything an iOS app contains, as Android's `appDefinition { }`: its style pack, its features in
 * tab order, an optional start tab (a route id) and the app's own Koin modules. The rules a
 * definition follows are checked in Kotlin ([validationError]), the same as on Android.
 */
public struct AppDefinition {
    public let style: StylePack
    public let startTab: String?
    public let modules: [Koin_coreModule]
    public let features: [any Feature]

    public init(style: StylePack, startTab: String? = nil, modules: [Koin_coreModule] = [], @FeatureBuilder _ features: () -> [any Feature]) {
        self.style = style
        self.startTab = startTab
        self.modules = modules
        self.features = features()
    }

    /** The platform-neutral definition the shared shell model runs on. */
    public var spec: DefinitionSpec {
        DefinitionSpec(features: features.map(\.spec), startTabRoute: startTab)
    }

    /** What the shared rules reject in this definition, or nil when it's valid. */
    public var validationError: String? { spec.validationError() }

    var gate: (any Feature)? { features.first { $0.spec.isGate } }

    func feature(forTab route: String) -> (any Feature)? { features.first { $0.spec.tabRoute == route } }

    @MainActor func view(for destination: Destination) -> AnyView? {
        for feature in features {
            if let view = feature.routes(destination.route, destination.args) { return view }
        }
        return nil
    }
}
