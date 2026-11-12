import XCTest
import Shared
@testable import WhitelabelKit

/** `<scheme>://catalog/<id>` opens the item; anything else is not the catalog's (as Android's `catalog/{id}`). */
final class CatalogLinkTests: XCTestCase {
    private let catalog = Catalog()

    private func id(_ link: String) -> String? {
        catalog.destination(forLink: URL(string: link)!)?.args["id"]
    }

    func testOpensTheItemOfOneSegment() {
        XCTAssertEqual(id("showcase://catalog/abc-123"), "abc-123")
        XCTAssertEqual(id("showcase://CATALOG/abc-123"), "abc-123")
        XCTAssertEqual(catalog.destination(forLink: URL(string: "showcase://catalog/abc")!)?.route, CatalogRoutes.shared.DETAIL)
    }

    func testRejectsAMissingIdOrExtraSegments() {
        XCTAssertNil(id("showcase://catalog"))
        XCTAssertNil(id("showcase://catalog/"))
        XCTAssertNil(id("showcase://catalog/abc/reviews"))
        XCTAssertNil(id("showcase://reset-password/abc"))
    }

    func testAnEncodedSlashStaysInTheId() {
        XCTAssertEqual(id("showcase://catalog/a%2Fb"), "a/b")
    }
}
