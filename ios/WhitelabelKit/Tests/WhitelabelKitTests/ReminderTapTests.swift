import XCTest
@testable import WhitelabelKit

/** A tapped reminder opens its deep link through the shell's link handling, also when it arrives before the app is ready. */
@MainActor
final class ReminderTapTests: XCTestCase {

    func testTapOpensTheDeepLink() {
        let taps = ReminderNotifications()
        var opened: [URL] = []
        taps.connect { opened.append($0) }

        taps.handleTap(userInfo: ["deepLink": "app://creature/n"])

        XCTAssertEqual(opened, [URL(string: "app://creature/n")!])
    }

    func testTapWithoutLinkOpensNothing() {
        let taps = ReminderNotifications()
        var opened: [URL] = []
        taps.connect { opened.append($0) }

        taps.handleTap(userInfo: [:])
        taps.handleTap(userInfo: ["deepLink": ""])

        XCTAssertEqual(opened, [])
    }

    /** A cold launch from a notification: the tap comes before the shell exists, and waits for it. */
    func testTapBeforeContentIsKeptPending() {
        let taps = ReminderNotifications()
        taps.handleTap(userInfo: ["deepLink": "app://creature/n"])
        var opened: [URL] = []

        taps.connect { opened.append($0) }

        XCTAssertEqual(opened, [URL(string: "app://creature/n")!])
        taps.connect { opened.append($0) }
        XCTAssertEqual(opened.count, 1, "a pending tap is delivered once")
    }

    /** iOS doesn't promise the delegate runs on the main thread: a tap from elsewhere must still arrive, not trap. */
    func testATapFromABackgroundThreadIsDelivered() async {
        let taps = ReminderNotifications()
        let opened = expectation(description: "opened")
        taps.connect { url in
            XCTAssertEqual(url, URL(string: "app://creature/n")!)
            opened.fulfill()
        }

        await Task.detached { taps.received(userInfo: ["deepLink": "app://creature/n"]) }.value

        await fulfillment(of: [opened], timeout: 2)
    }

    func testRemindersShowWhileTheAppIsOpen() {
        XCTAssertEqual(ReminderNotifications.foregroundPresentation, [.banner, .sound, .list])
    }
}
