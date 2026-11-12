import XCTest
import Shared
@testable import WhitelabelKit

/** Records when it is installed and whether the shared graph already ran, and the mode it got. */
@MainActor
private final class RecordingService: AppService {
    let kind: AppServiceKind
    private(set) var modes: [String] = []
    private(set) var graphStartedAtInstall: [Bool] = []

    init(kind: AppServiceKind = .firebase) { self.kind = kind }

    func install(mode: String) {
        modes.append(mode)
        graphStartedAtInstall.append(SharedGraph.shared.isStarted)
    }
}

@MainActor
final class AppServiceTests: XCTestCase {

    // AppRuntime.start declares Firebase on the shared graph; clear it so later tests start plain graphs.
    override func setUp() { TestGraph.stop(); SharedGraph.shared.clearFirebaseServices() }
    override func tearDown() { TestGraph.stop(); SharedGraph.shared.clearFirebaseServices() }

    func testServicesInstallBeforeTheGraphStarts() throws {
        let service = RecordingService()
        let definition = AppDefinition(style: .material, services: [service]) {
            StubFeature("settings", tab: SettingsRoutes.shared.ROOT)
        }
        let platform = IosPlatform(
            tokenCipher: KeychainTokenCipher(service: TestGraph.cipherService),
            language: IosLanguageRepository(defaults: UserDefaults(suiteName: "wl.tests")!),
            brand: TestGraph.brand,
            logger: { _ in },
            firebase: "off"
        )

        try AppRuntime.start(definition, platform: platform, useMockApi: false, mode: "off")

        XCTAssertEqual(service.modes, ["off"])
        XCTAssertEqual(service.graphStartedAtInstall, [false])
        XCTAssertTrue(SharedGraph.shared.isStarted)
        XCTAssertEqual(SharedGraph.shared.firebaseMode(), .off)
        // A restarted graph keeps what the services handed over.
        try SharedGraph.shared.resetForTests()
        XCTAssertEqual(SharedGraph.shared.firebaseMode(), .off)
    }

    private func platform(firebase: String = "off", ads: String = "off", declared: Bool = false) -> IosPlatform {
        IosPlatform(
            tokenCipher: KeychainTokenCipher(service: TestGraph.cipherService),
            language: IosLanguageRepository(defaults: UserDefaults(suiteName: "wl.tests")!),
            brand: TestGraph.brand,
            logger: { _ in },
            firebase: firebase,
            ads: ads,
            adsUnits: "",
            adsDeclared: declared,
            debugBuild: true
        )
    }

    func testEachServiceGetsItsOwnSettingsMode() throws {
        let firebase = RecordingService(kind: .firebase)
        let ads = RecordingService(kind: .ads)
        let definition = AppDefinition(style: .material, services: [firebase, ads], ads: [AdPlacement(name: "home", underTab: nil)]) {
            StubFeature("settings", tab: SettingsRoutes.shared.ROOT)
        }

        // Ads are off in this build: the ads service installs nothing, so the graph needs no SDK.
        try AppRuntime.start(definition, platform: platform(ads: "off", declared: true), useMockApi: false, mode: "fake")

        XCTAssertEqual(firebase.modes, ["fake"])
        XCTAssertEqual(ads.modes, ["off"])
        XCTAssertNotNil(SharedGraph.shared.adsController())
        // Ads wait for consent too, so the app has one to gather.
        XCTAssertNotNil(SharedGraph.shared.consentOrNull())
    }

    func testAnAdsOnlyAppDeclaresNoFirebase() throws {
        let definition = AppDefinition(style: .material, services: [RecordingService(kind: .ads)], ads: [AdPlacement(name: "home", underTab: nil)]) {
            StubFeature("settings", tab: SettingsRoutes.shared.ROOT)
        }
        XCTAssertNil(definition.serviceProblem(firebaseMode: "off", debugBuild: true))
        XCTAssertEqual(
            definition.serviceProblem(firebaseMode: "on", debugBuild: true),
            "firebase.enabled is set but the app definition doesn't call firebase()"
        )

        try AppRuntime.start(definition, platform: platform(declared: true), useMockApi: false, mode: "off")

        XCTAssertEqual(SharedGraph.shared.firebaseMode(), .off)
        XCTAssertNotNil(SharedGraph.shared.consentOrNull())
    }

    func testFirebaseSettingWithoutServiceFailsWithAndroidsMessage() {
        let bare = AppDefinition(style: .material) { StubFeature("settings", tab: "s") }
        let message = "firebase.enabled is set but the app definition doesn't call firebase()"
        for debug in [true, false] {
            XCTAssertEqual(bare.serviceProblem(firebaseMode: "on", debugBuild: debug), message)
            XCTAssertEqual(bare.serviceProblem(firebaseMode: "fake", debugBuild: debug), message)
            XCTAssertNil(bare.serviceProblem(firebaseMode: "off", debugBuild: debug))
        }
    }

    func testServiceWithTheSettingOffFailsInDebugAndRunsOffInRelease() {
        let withService = AppDefinition(style: .material, services: [RecordingService()]) { StubFeature("settings", tab: "s") }
        XCTAssertEqual(
            withService.serviceProblem(firebaseMode: "off", debugBuild: true),
            "firebase() needs firebase.enabled=true or debug in app.properties"
        )
        // Release with firebase.enabled=debug: the service is declared and the setting is off.
        XCTAssertNil(withService.serviceProblem(firebaseMode: "off", debugBuild: false))
        for debug in [true, false] {
            XCTAssertNil(withService.serviceProblem(firebaseMode: "on", debugBuild: debug))
            XCTAssertNil(withService.serviceProblem(firebaseMode: "fake", debugBuild: debug))
        }
    }
}
