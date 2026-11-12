import SwiftUI
import XCTest
import Shared
@testable import WhitelabelKit
@testable import WhitelabelFirebase

/** A tab-only feature, so the graph starts without a backend. */
private struct SettingsTab: Feature {
    var spec: FeatureSpec {
        FeatureSpec(id: "settings", flagKey: nil, tabRoute: SettingsRoutes.shared.ROOT, requiresSignIn: false, isGate: false, requiresBackend: false)
    }
    func tab() -> TabItem? { TabItem(route: SettingsRoutes.shared.ROOT, title: "Settings", systemImage: "gear") }
    func routes(_ route: String, _ args: [String: String]) -> AnyView? { nil }
}

@MainActor
final class FirebaseServiceTests: XCTestCase {

    override func setUp() { SharedGraph.shared.stop(); SharedGraph.shared.clearFirebaseServices() }
    override func tearDown() { SharedGraph.shared.stop(); SharedGraph.shared.clearFirebaseServices() }

    private func platform(firebase: String) -> IosPlatform {
        IosPlatform(
            tokenCipher: KeychainTokenCipher(service: "com.whitelabel.firebase.tests"),
            language: IosLanguageRepository(defaults: UserDefaults(suiteName: "wl.firebase.tests")!),
            brand: BrandConfig(
                tenant: nil, appName: "Showcase", applicationId: "com.whitelabel.showcase.tests", versionName: "1.0",
                environment: .development, apiBaseUrl: nil, deepLinkScheme: "showcase",
                runtimeDefaults: RuntimeDefaults(supportEmail: nil, privacyPolicyUrl: "https://p", termsUrl: "https://t", enabledFeatures: [])
            ),
            logger: { _ in },
            firebase: firebase
        )
    }

    private var definition: AppDefinition {
        AppDefinition(style: .material, services: [FirebaseService()]) { SettingsTab() }
    }

    func testFakeModeRegistersTheFakeConsent() throws {
        // What DebugControls passes for `-fakeConsent declined`.
        SharedGraph.shared.useFirebaseDebug(fake: false, analytics: false, fakeConsent: "declined")

        try AppRuntime.start(definition, platform: platform(firebase: "fake"), useMockApi: false, mode: "fake")
        SharedGraph.shared.consent().gatherOnLaunch()

        XCTAssertEqual(SharedGraph.shared.firebaseMode(), .fake)
        XCTAssertEqual(
            SharedGraph.shared.consent().state.value as? ConsentStateResolved,
            ConsentStateResolved(canRequestAds: true, analyticsAllowed: false)
        )
    }

    func testOffModeRegistersNothing() throws {
        try AppRuntime.start(definition, platform: platform(firebase: "off"), useMockApi: false, mode: "off")
        SharedGraph.shared.consent().gatherOnLaunch()

        XCTAssertEqual(SharedGraph.shared.firebaseMode(), .off)
        // No consent port was handed over, so consent resolves to everything denied without a form.
        XCTAssertEqual(
            SharedGraph.shared.consent().state.value as? ConsentStateResolved,
            ConsentStateResolved(canRequestAds: false, analyticsAllowed: false)
        )
    }
}
