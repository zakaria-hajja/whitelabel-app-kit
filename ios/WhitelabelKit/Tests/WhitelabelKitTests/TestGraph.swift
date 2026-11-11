import Foundation
import Shared
@testable import WhitelabelKit

/** Starts the shared graph on the fake backend with a settings-only definition, as a test needs it. */
enum TestGraph {
    static let brand = BrandConfig(
        tenant: nil, appName: "Showcase", applicationId: "com.whitelabel.showcase.tests", versionName: "1.0",
        environment: .development, apiBaseUrl: "https://api.showcase.example/api/v1/", deepLinkScheme: "showcase",
        runtimeDefaults: RuntimeDefaults(supportEmail: nil, privacyPolicyUrl: "https://p", termsUrl: "https://t", enabledFeatures: [])
    )

    static func start() throws {
        IosMock.shared.enable()
        let definition = DefinitionSpec(
            features: [FeatureSpec(id: "settings", flagKey: nil, tabRoute: SettingsRoutes.shared.ROOT, requiresSignIn: false, isGate: false, requiresBackend: false)],
            startTabRoute: nil
        )
        let platform = IosPlatform(
            tokenCipher: KeychainTokenCipher(service: "com.whitelabel.kit.tests"),
            language: IosLanguageRepository(defaults: UserDefaults(suiteName: "wl.tests")!),
            brand: brand,
            logger: { _ in }
        )
        try SharedGraph.shared.start(platform: platform, definition: definition, appModules: [], useMockApi: true)
    }

    static func stop() { SharedGraph.shared.stop() }
}
