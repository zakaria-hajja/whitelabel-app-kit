import UIKit
import Shared

/**
 * Drives an app's content gate (its lock) from the app lifecycle, as Android's process lifecycle does:
 * `UIApplication.willEnterForegroundNotification` → `foreground()`, `didEnterBackgroundNotification`
 * → `background()`. These are app-level (posted when the first scene enters the foreground and when
 * the last one leaves), and not posted on a cold launch, which the gate's contract covers.
 *
 * Each handler runs synchronously inside `post` on the main thread and re-reads the gate's state in
 * the same call, so SwiftUI's first frame after returning is already the lock: never hop through a
 * `Task` here. Changes the gate makes on its own (unlock, the first read) arrive through [refresh].
 *
 * One per process, owned by `AppRuntime`; nil for an app without a gate.
 */
@MainActor
@Observable
final class ContentGateDriver {
    private(set) var state: ContentGateState

    @ObservationIgnored private let read: () -> ContentGateState
    @ObservationIgnored private let foreground: () -> Void
    @ObservationIgnored private let background: () -> Void
    // Observers on a NotificationCenter go with the driver; AppRuntime keeps its one for the process.
    @ObservationIgnored private var observers: [NSObjectProtocol] = []

    init(
        read: @escaping () -> ContentGateState,
        foreground: @escaping () -> Void,
        background: @escaping () -> Void,
        center: NotificationCenter = .default
    ) {
        self.read = read
        self.foreground = foreground
        self.background = background
        state = read()
        // queue nil: the block runs synchronously on the posting thread, which UIKit makes the main one.
        observers = [
            center.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: nil) { [weak self] _ in
                MainActor.assumeIsolated { self?.enterForeground() }
            },
            center.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: nil) { [weak self] _ in
                MainActor.assumeIsolated { self?.enterBackground() }
            },
        ]
    }

    /** Follows the gate's state; called for each change the gate makes on its own. */
    func refresh() {
        state = read()
    }

    /** The root screen: splash, lock or the app's content. */
    func screen(showsContent: Bool) -> ShellScreen {
        ContentGateKt.shellScreen(showsContent: showsContent, gate: state)
    }

    private func enterForeground() {
        foreground()
        state = read()
    }

    private func enterBackground() {
        background()
        state = read()
    }
}
