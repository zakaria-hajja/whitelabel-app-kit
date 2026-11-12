import XCTest
import Shared
@testable import WhitelabelKit

/** Records when it is installed and whether the shared graph already ran, and the mode it got. */
@MainActor
private final class RecordingService: AppService {
    private(set) var modes: [String] = []
    private(set) var graphStartedAtInstall: [Bool] = []

    func install(mode: String) {
        modes.append(mode)
        graphStartedAtInstall.append(SharedGraph.shared.isStarted)
    }
}

@MainActor
final class AppServiceTests: XCTestCase {

    override func setUp() { TestGraph.stop() }
    override func tearDown() { TestGraph.stop() }

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

    func testFirebaseSettingWithoutServiceFailsWithAndroidsMessage() {
        let bare = AppDefinition(style: .material) { StubFeature("settings", tab: "s") }
        XCTAssertEqual(bare.serviceProblem(firebaseMode: "on"), "firebase.enabled is set but the app definition doesn't call firebase()")
        XCTAssertEqual(bare.serviceProblem(firebaseMode: "fake"), "firebase.enabled is set but the app definition doesn't call firebase()")
        XCTAssertNil(bare.serviceProblem(firebaseMode: "off"))

        let withService = AppDefinition(style: .material, services: [RecordingService()]) { StubFeature("settings", tab: "s") }
        XCTAssertNil(withService.serviceProblem(firebaseMode: "on"))
        // An app with the service and the setting off (a release build of "debug") runs in off mode.
        XCTAssertNil(withService.serviceProblem(firebaseMode: "off"))
    }
}
