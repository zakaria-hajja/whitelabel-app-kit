import XCTest
import Shared
@testable import WhitelabelKit

@MainActor
final class PrivacyChoicesRowTests: XCTestCase {

    override func setUp() { TestGraph.stop(); SharedGraph.shared.clearFirebaseServices() }
    override func tearDown() { TestGraph.stop(); SharedGraph.shared.clearFirebaseServices() }

    private func startGraph(optionsRequired: Bool) throws -> FakeConsentPort {
        let port = FakeConsentPort(next: nil, optionsRequired: optionsRequired)
        SharedGraph.shared.useConsent(port: port)
        try TestGraph.start()
        SharedGraph.shared.consent().gatherOnLaunch()
        return port
    }

    func testHiddenWhenNotRequired() throws {
        _ = try startGraph(optionsRequired: false)
        let model = PrivacyChoicesModel(consent: SharedGraph.shared.consentOrNull())
        XCTAssertFalse(model.visible)
    }

    func testShowsAndOpensWhenRequired() throws {
        let port = try startGraph(optionsRequired: true)
        let model = PrivacyChoicesModel(consent: SharedGraph.shared.consentOrNull())
        XCTAssertTrue(model.visible)
        model.open()
        XCTAssertEqual(port.optionsShown, 1)
    }

    func testHiddenWithoutFirebase() throws {
        try TestGraph.start()
        XCTAssertNil(SharedGraph.shared.consentOrNull())
        let model = PrivacyChoicesModel(consent: SharedGraph.shared.consentOrNull())
        XCTAssertFalse(model.visible)
        model.open()
    }
}
