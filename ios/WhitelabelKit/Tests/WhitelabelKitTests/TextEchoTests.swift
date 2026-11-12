import XCTest
@testable import WhitelabelKit

/**
 * A field's text goes to the shared ViewModel and comes back a moment later. Reading it back as the
 * field's value drops what was typed meanwhile ("Item 1" became "Itm 1"); TextEcho tells the echo
 * of what was typed from a change the ViewModel made itself.
 */
final class TextEchoTests: XCTestCase {

    func testEchoesOfTypedTextAreIgnored() {
        var echo = TextEcho()
        echo.typed("I"); echo.typed("It"); echo.typed("Ite")
        XCTAssertNil(echo.received("I"))
        XCTAssertNil(echo.received("It"))
        XCTAssertNil(echo.received("Ite"))
    }

    func testAChangeTheViewModelMadeIsAdopted() {
        var echo = TextEcho()
        echo.typed("secret")
        XCTAssertEqual(echo.received(""), "") // e.g. cleared after a successful change
    }

    func testAnEchoAfterAnAdoptedChangeIsStillIgnored() {
        var echo = TextEcho()
        echo.typed("a")
        XCTAssertEqual(echo.received("x"), "x")
        echo.typed("xy")
        XCTAssertNil(echo.received("xy"))
    }
}
