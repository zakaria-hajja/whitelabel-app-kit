import XCTest
@testable import WhitelabelKit

/**
 * A field's text goes to the shared ViewModel and comes back a moment later. Reading it back as the
 * field's value drops what was typed meanwhile ("Item 1" became "Itm 1"); TextEcho tells the echo
 * of what was typed from a change the ViewModel made itself, and when an edit must be sent.
 */
final class TextEchoTests: XCTestCase {

    func testEchoesOfTypedTextAreIgnored() {
        var echo = TextEcho(value: "")
        for text in ["I", "It", "Ite"] { XCTAssertTrue(echo.edited(text)) }
        XCTAssertNil(echo.received("I"))
        XCTAssertNil(echo.received("It"))
        XCTAssertNil(echo.received("Ite"))
    }

    func testAChangeTheViewModelMadeIsAdopted() {
        var echo = TextEcho(value: "")
        XCTAssertTrue(echo.edited("secret"))
        XCTAssertEqual(echo.received(""), "") // e.g. cleared after a successful change
    }

    func testAnEchoAfterAnAdoptedChangeIsStillIgnored() {
        var echo = TextEcho(value: "")
        XCTAssertTrue(echo.edited("a"))
        XCTAssertEqual(echo.received("x"), "x")
        XCTAssertTrue(echo.edited("xy"))
        XCTAssertNil(echo.received("xy"))
    }

    /** Typed a letter and deleted it before the echo came back: the deletion must still be sent. */
    func testAnEditBackToTheLastReceivedTextIsSent() {
        var echo = TextEcho(value: "Ada")
        XCTAssertTrue(echo.edited("Adax"))
        XCTAssertTrue(echo.edited("Ada"))
        XCTAssertNil(echo.received("Adax"))
        XCTAssertNil(echo.received("Ada"))
    }

    /** Adopting the ViewModel's text (or showing the first value) is not an edit to send back. */
    func testTheReceivedTextItselfIsNotSent() {
        var echo = TextEcho(value: "Ada")
        XCTAssertFalse(echo.edited("Ada"))
        XCTAssertEqual(echo.received("Bob"), "Bob")
        XCTAssertFalse(echo.edited("Bob"))
    }

    /** Conflated emissions with repeated text: the newest typed text clears what is pending. */
    func testTheNewestTypedTextClearsEverythingPending() {
        var echo = TextEcho(value: "Ad")
        for text in ["Ada", "Ada ", "Ada", "Ada "] { _ = echo.edited(text) }
        XCTAssertNil(echo.received("Ada ")) // the only emission that arrives
        XCTAssertEqual(echo.received("Ada"), "Ada") // a trim the ViewModel made itself
    }
}
