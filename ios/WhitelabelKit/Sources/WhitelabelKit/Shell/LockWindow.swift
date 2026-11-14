import SwiftUI
import UIKit

/**
 * Shows the app's lock in its own window above everything the app presents: sheets and full-screen
 * covers (which SwiftUI dismisses **animated** when the lock replaces the content), alerts, and
 * controllers UIKit presented (the share sheet). The shell puts it in its root while the screen is
 * the lock; the window goes when the anchor leaves (unlock). Review B1.
 */
struct LockWindowPresenter: UIViewRepresentable {
    let lock: AnyView

    func makeUIView(context: UIViewRepresentableContext<Self>) -> LockWindowAnchor { LockWindowAnchor(lock: lock) }

    func updateUIView(_ anchor: LockWindowAnchor, context: UIViewRepresentableContext<Self>) { anchor.update(lock) }

    static func dismantleUIView(_ anchor: LockWindowAnchor, coordinator: ()) { anchor.removeLockWindow() }
}

/**
 * An invisible view that owns the lock window while it is in a window: the lock window joins that
 * window's scene, synchronously, in the same layout pass that put the lock in the shell's root, so
 * the first frame after the app returns already has it on top.
 */
final class LockWindowAnchor: UIView {
    private(set) var lockWindow: UIWindow?
    private let host: UIHostingController<AnyView>

    init(lock: AnyView) {
        host = UIHostingController(rootView: lock)
        super.init(frame: .zero)
        isUserInteractionEnabled = false
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    func update(_ lock: AnyView) {
        host.rootView = lock
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if let scene = window?.windowScene {
            showLockWindow(on: scene)
        } else {
            removeLockWindow()
        }
    }

    private func showLockWindow(on scene: UIWindowScene) {
        guard lockWindow == nil else { return }
        let lockWindow = UIWindow(windowScene: scene)
        // Above alerts, so nothing the app shows covers the lock.
        lockWindow.windowLevel = .alert + 1
        lockWindow.rootViewController = host
        // Key: a text field under the lock loses the keyboard.
        lockWindow.makeKeyAndVisible()
        self.lockWindow = lockWindow
    }

    func removeLockWindow() {
        guard let lockWindow else { return }
        lockWindow.isHidden = true
        lockWindow.rootViewController = nil
        self.lockWindow = nil
        // The app's own window takes the keyboard focus back.
        lockWindow.windowScene?.windows.first { !$0.isHidden && $0.windowLevel == .normal }?.makeKey()
    }
}
