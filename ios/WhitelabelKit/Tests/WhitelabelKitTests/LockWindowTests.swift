import SwiftUI
import UIKit
import XCTest
@testable import WhitelabelKit

/**
 * Review B1: while locked, the lock sits in its own window above everything the app presents (sheets,
 * covers, alerts, UIKit controllers), from the moment the shell's root becomes the lock.
 */
@MainActor
final class LockWindowTests: XCTestCase {

    private func hostWindow() throws -> UIWindow {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.isHidden = false
        return window
    }

    func testInAWindow_theLockShowsAboveAlerts() throws {
        let host = try hostWindow()
        let anchor = LockWindowAnchor(lock: AnyView(Text(verbatim: "Lock")))

        host.addSubview(anchor)

        let lock = try XCTUnwrap(anchor.lockWindow)
        XCTAssertFalse(lock.isHidden)
        XCTAssertGreaterThan(lock.windowLevel, UIWindow.Level.alert)
        XCTAssertTrue(lock.windowScene === host.windowScene)
        anchor.removeFromSuperview()
        host.isHidden = true
    }

    /// Review B2: VoiceOver reads every visible window, so the lock window must be modal or it reads
    /// the share sheet or the dismissing sheet under the lock.
    func testInAWindow_theLockIsModalForVoiceOver() throws {
        let host = try hostWindow()
        let anchor = LockWindowAnchor(lock: AnyView(Text(verbatim: "Lock")))

        host.addSubview(anchor)

        let lock = try XCTUnwrap(anchor.lockWindow)
        XCTAssertTrue(lock.accessibilityViewIsModal)
        anchor.removeFromSuperview()
        host.isHidden = true
    }

    /// Review B2: VoiceOver moves to the lock when it shows, and back to the app when it goes.
    func testLockAndUnlock_announceAScreenChange() throws {
        let host = try hostWindow()
        var posted: [(UIAccessibility.Notification, Any?)] = []
        let anchor = LockWindowAnchor(lock: AnyView(Text(verbatim: "Lock"))) { posted.append(($0, $1)) }

        host.addSubview(anchor)
        let lockRoot = try XCTUnwrap(anchor.lockWindow?.rootViewController?.view)

        XCTAssertEqual(posted.map(\.0), [.screenChanged])
        XCTAssertTrue((posted.first?.1 as AnyObject?) === lockRoot)

        anchor.removeFromSuperview()

        XCTAssertEqual(posted.map(\.0), [.screenChanged, .screenChanged])
        XCTAssertNil(posted.last?.1)
        host.isHidden = true
    }

    /// Review N9: unlock gives key back to the window the lock took it from, not to the first
    /// normal-level window of the scene (an SDK's, or another app window).
    func testUnlock_givesKeyBackToTheWindowItTookItFrom() throws {
        let other = try hostWindow()
        let host = try hostWindow()
        host.makeKey()
        let anchor = LockWindowAnchor(lock: AnyView(Text(verbatim: "Lock")))
        host.addSubview(anchor)
        XCTAssertTrue(try XCTUnwrap(anchor.lockWindow).isKeyWindow)

        anchor.removeFromSuperview()

        XCTAssertTrue(host.isKeyWindow)
        host.isHidden = true
        other.isHidden = true
    }

    func testLeavingTheWindow_removesTheLock() throws {
        let host = try hostWindow()
        let anchor = LockWindowAnchor(lock: AnyView(Text(verbatim: "Lock")))
        host.addSubview(anchor)
        let lock = try XCTUnwrap(anchor.lockWindow)

        anchor.removeFromSuperview()

        XCTAssertTrue(lock.isHidden)
        XCTAssertNil(anchor.lockWindow)
        host.isHidden = true
    }
}
