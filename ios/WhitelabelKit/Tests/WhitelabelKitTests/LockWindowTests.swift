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
