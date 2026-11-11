import SwiftUI
import XCTest
import Shared
@testable import WhitelabelKit

/** A feature with only a spec (and optionally a deep link), to build definitions in tests. */
struct StubFeature: Feature {
    let spec: FeatureSpec
    var link: ((URL) -> Destination?)? = nil

    init(_ id: String, tab: String? = nil, gate: Bool = false, signIn: Bool = false, link: ((URL) -> Destination?)? = nil) {
        spec = FeatureSpec(id: id, flagKey: nil, tabRoute: tab, requiresSignIn: signIn, isGate: gate, requiresBackend: false)
        self.link = link
    }

    func tab() -> TabItem? { spec.tabRoute.map { TabItem(route: $0, title: spec.id, systemImage: "circle") } }
    func routes(_ route: String, _ args: [String: String]) -> AnyView? { nil }
    func destination(forLink url: URL) -> Destination? { link?(url) }
}

final class AppDefinitionTests: XCTestCase {

    func testMapsFeaturesToTheSpecInOrder() {
        let definition = AppDefinition(style: .material, startTab: "b") {
            StubFeature("a", tab: "a")
            StubFeature("b", tab: "b")
        }
        XCTAssertEqual(definition.spec.features.map(\.id), ["a", "b"])
        XCTAssertEqual(definition.spec.startTabRoute, "b")
        XCTAssertNil(definition.validationError)
    }

    func testInvalidDefinitionReportsTheKotlinMessage() {
        let definition = AppDefinition(style: .material) {
            StubFeature("g1", gate: true)
            StubFeature("g2", gate: true)
            StubFeature("a", tab: "a")
        }
        XCTAssertEqual(definition.validationError, "Only one feature may gate the app: g1, g2")
    }
}

/** Swift finds which feature a link belongs to; the shared policy decides what happens to it. */
final class DeepLinksTests: XCTestCase {
    private let item = StubFeature("catalog", tab: "catalog") { url in
        url.host == "catalog" ? Destination("catalog/detail", args: ["id": url.lastPathComponent]) : nil
    }
    private let reset = StubFeature("auth", gate: true) { url in url.host == "reset-password" ? Destination("auth/reset") : nil }

    private func route(_ link: String, _ features: [any Feature], enabled: Set<String>? = nil, signedIn: Bool = false) -> DeepLinkRouting {
        DeepLinks.route(URL(string: link)!, scheme: "showcase", features: features, enabled: enabled ?? Set(features.map(\.spec.id)), signedIn: signedIn)
    }

    func testOpensAMainLinkWithoutAGate() {
        XCTAssertEqual(route("showcase://catalog/42", [item]), .open(Destination("catalog/detail", args: ["id": "42"]), tab: "catalog", inGate: false))
    }

    func testHoldsAMainLinkUntilSignIn() {
        XCTAssertEqual(route("showcase://catalog/42", [reset, item]), .hold)
    }

    func testIgnoresAResetLinkWhileSignedIn() {
        XCTAssertEqual(route("showcase://reset-password?token=t", [reset, item], signedIn: true), .ignore)
    }

    func testOpensAResetLinkInTheGateWhileSignedOut() {
        XCTAssertEqual(route("showcase://reset-password?token=t", [reset, item]), .open(Destination("auth/reset"), tab: nil, inGate: true))
    }

    /** A feature its flag switched off is off entirely: its links are dropped (as on Android, where it registers no routes). */
    func testIgnoresALinkIntoAFeatureThatIsOff() {
        XCTAssertEqual(route("showcase://catalog/42", [item], enabled: []), .ignore)
    }

    func testIgnoresAnotherSchemeAndUnknownLinks() {
        XCTAssertEqual(route("other://catalog/42", [item]), .ignore)
        XCTAssertEqual(route("showcase://nowhere", [item]), .ignore)
    }

    func testFakeControlLinksAreCommands() {
        XCTAssertEqual(route("showcase://_fake/offline", [item]), .control("offline"))
    }
}

/** A link is only decided once the shell shows content (tabs decided, session known), as on Android. */
final class PendingLinkTests: XCTestCase {
    func testWaitsWhileTheShellIsNotReady() {
        XCTAssertEqual(DeepLinks.decide(showsContent: false, routing: { .open(Destination("a"), tab: "a", inGate: false) }), .wait)
    }

    func testKeepsAHeldLink() {
        XCTAssertEqual(DeepLinks.decide(showsContent: true, routing: { .hold }), .wait)
    }

    func testConsumesAnOpenedOrIgnoredLink() {
        let open = DeepLinkRouting.open(Destination("a"), tab: "a", inGate: false)
        XCTAssertEqual(DeepLinks.decide(showsContent: true, routing: { open }), .consume(open))
        XCTAssertEqual(DeepLinks.decide(showsContent: true, routing: { .ignore }), .consume(.ignore))
    }
}
