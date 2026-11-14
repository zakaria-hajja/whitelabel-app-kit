import Foundation
import UIKit
import XCTest
import Shared
@testable import WhitelabelKit

/**
 * The driver reports the app's foreground/background to the gate and re-reads its state in the same
 * call, so the first frame after returning is already the lock (no await, no hop).
 */
@MainActor
final class ContentGateDriverTests: XCTestCase {

    private var current = ContentGateState.open
    private var backgrounds = 0
    private var foregrounds = 0
    private let center = Foundation.NotificationCenter()

    private func makeDriver(onForeground: @escaping () -> Void = {}, onBackground: @escaping () -> Void = {}) -> ContentGateDriver {
        ContentGateDriver(
            read: { [unowned self] in current },
            foreground: { [unowned self] in foregrounds += 1; onForeground() },
            background: { [unowned self] in backgrounds += 1; onBackground() },
            center: center
        )
    }

    func testForegroundNotification_locksBeforeAnyAwait() {
        let driver = makeDriver(onForeground: { [unowned self] in current = .locked })
        XCTAssertEqual(driver.state, .open)
        center.post(name: UIApplication.willEnterForegroundNotification, object: nil)
        XCTAssertEqual(foregrounds, 1)
        XCTAssertEqual(driver.state, .locked)
        XCTAssertEqual(driver.screen(showsContent: true), .lock)
    }

    func testBackgroundNotification_callsBackgroundOnce() {
        let driver = makeDriver(onBackground: { [unowned self] in current = .locked })
        center.post(name: UIApplication.didEnterBackgroundNotification, object: nil)
        XCTAssertEqual(backgrounds, 1)
        XCTAssertEqual(foregrounds, 0)
        XCTAssertEqual(driver.state, .locked)
    }

    func testSeedsFromRead() {
        current = .locked
        XCTAssertEqual(makeDriver().state, .locked)
    }

    func testRefresh_followsRead() {
        current = .locked
        let driver = makeDriver()
        current = .open
        driver.refresh()
        XCTAssertEqual(driver.state, .open)
        XCTAssertEqual(driver.screen(showsContent: true), .content)
    }

    func testScreen_pendingIsSplash() {
        current = .pending
        let driver = makeDriver()
        XCTAssertEqual(driver.screen(showsContent: true), .splash)
        current = .open
        driver.refresh()
        XCTAssertEqual(driver.screen(showsContent: false), .splash)
    }
}
