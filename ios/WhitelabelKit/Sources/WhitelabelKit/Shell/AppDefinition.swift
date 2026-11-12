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
    public let services: [any AppService]

    public init(style: StylePack, startTab: String? = nil, modules: [Koin_coreModule] = [], services: [any AppService] = [], @FeatureBuilder _ features: () -> [any Feature]) {
        self.style = style
        self.startTab = startTab
        self.modules = modules
        self.services = services
        self.features = features()
    }

    /** The platform-neutral definition the shared shell model runs on. */
    public var spec: DefinitionSpec {
        DefinitionSpec(features: features.map(\.spec), startTabRoute: startTab)
    }

    /** What the shared rules reject in this definition, or nil when it's valid. */
    public var validationError: String? { spec.validationError() }

    /**
     * What stops the app when the Firebase setting (`WL_FIREBASE`: on, fake or off) and the services
     * disagree, with Android's message; nil when they agree.
     * - Setting on or fake without a service: the definition doesn't call firebase().
     * - A service with the setting off in a debug build: the generator only writes off there when
     *   `firebase.enabled=false`, so the setting is missing (`debug` gives fake or on in Debug).
     *   In a release build off is legitimate (`firebase.enabled=debug`): the app runs in off mode.
     */
    func serviceProblem(firebaseMode: String, debugBuild: Bool) -> String? {
        if services.isEmpty, firebaseMode == "on" || firebaseMode == "fake" {
            return "firebase.enabled is set but the app definition doesn't call firebase()"
        }
        if !services.isEmpty, firebaseMode == "off", debugBuild {
            return "firebase() needs firebase.enabled=true or debug in app.properties"
        }
        return nil
    }

    var gate: (any Feature)? { features.first { $0.spec.isGate } }

    func feature(forTab route: String) -> (any Feature)? { features.first { $0.spec.tabRoute == route } }

    @MainActor func view(for destination: Destination) -> AnyView? {
        for feature in features {
            if let view = feature.routes(destination.route, destination.args) { return view }
        }
        return nil
    }
}
