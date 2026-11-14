import SwiftUI
import Shared
import WhitelabelKit

/**
 * Debug only: with `-uiTestContentGate`, showcase runs behind the kit's content gate with
 * `UiTestContentGate` (locks on every return to the foreground) and a Settings row that opens a sheet
 * and, from it, a full-screen cover. The kit's UI tests prove that a relock hides both. Without the
 * argument (and in Release) showcase has no lock.
 */
@MainActor
enum UiTestContentGateSetup {
    #if DEBUG
    static let isOn = ProcessInfo.processInfo.arguments.contains("-uiTestContentGate")
    #else
    static let isOn = false
    #endif

    static var modules: [Koin_coreModule] {
        #if DEBUG
        if isOn { FirstFrameProbe.shared.install() }
        return isOn ? [IosMock.shared.uiTestContentGateModule()] : []
        #else
        return []
        #endif
    }

    static var lockScreen: (@MainActor () -> AnyView)? {
        #if DEBUG
        if isOn {
            return { AnyView(UiTestLockView()) }
        }
        #endif
        return nil
    }
}

#if DEBUG
/**
 * What the user sees in the first frame after the app returns: on the first display-link tick after
 * `willEnterForeground`, "presented" when the topmost window still shows a presented controller
 * (a sheet or cover over the lock), else "lock-on-top". UI tests can't see frames; this can.
 */
@MainActor
@Observable
final class FirstFrameProbe {
    static let shared = FirstFrameProbe()
    private(set) var value = "none"
    @ObservationIgnored private var observer: NSObjectProtocol?
    @ObservationIgnored private var link: CADisplayLink?

    func install() {
        guard observer == nil else { return }
        observer = NotificationCenter.default.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: nil) { _ in
            MainActor.assumeIsolated { FirstFrameProbe.shared.armForNextFrame() }
        }
    }

    private func armForNextFrame() {
        link?.invalidate()
        link = CADisplayLink(target: self, selector: #selector(tick))
        link?.add(to: .main, forMode: .common)
    }

    @objc private func tick() {
        link?.invalidate()
        link = nil
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        // The app's own windows: UIKit's text-effects and keyboard windows sit above them and present nothing.
        let windows = (scene?.windows ?? []).filter { !$0.isHidden && !"\(type(of: $0))".contains("TextEffects") && !"\(type(of: $0))".contains("Keyboard") }
        let top = windows.max { $0.windowLevel < $1.windowLevel }
        value = top?.rootViewController?.presentedViewController == nil ? "lock-on-top" : "presented"
        detail = (scene?.windows ?? []).map { window in
            var chain: [String] = []
            var vc = window.rootViewController?.presentedViewController
            while let current = vc {
                chain.append("\(type(of: current))\(current.isBeingDismissed ? "(dismissing)" : "")")
                vc = current.presentedViewController
            }
            return "\(type(of: window))@\(window.windowLevel.rawValue) hidden=\(window.isHidden) presents=\(chain)"
        }.joined(separator: " | ")
    }

    private(set) var detail = ""
}

private struct UiTestLockView: View {
    var body: some View {
        VStack(spacing: 24) {
            Text(verbatim: "Locked").accessibilityIdentifier("uitest.lock")
            Text(verbatim: FirstFrameProbe.shared.value).accessibilityIdentifier("uitest.firstFrame")
            Text(verbatim: FirstFrameProbe.shared.detail).font(.caption2).accessibilityIdentifier("uitest.firstFrameDetail")
            Button { IosMock.shared.unlockUiTestContentGate() } label: { Text(verbatim: "Unlock") }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemBackground))
    }
}
#endif

/** Opens a sheet, and from it a full-screen cover: what a screen of the app has open when it relocks. */
struct UiTestPresentationsRow: View {
    @State private var sheet = false
    @State private var cover = false

    var body: some View {
        Button { sheet = true } label: { Text(verbatim: "Open probe sheet") }
            .sheet(isPresented: $sheet) {
                VStack(spacing: 24) {
                    Text(verbatim: "Probe sheet").accessibilityIdentifier("probe.sheet")
                    Button { cover = true } label: { Text(verbatim: "Open probe cover") }
                }
                .fullScreenCover(isPresented: $cover) {
                    Text(verbatim: "Probe cover").accessibilityIdentifier("probe.cover")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color.orange)
                }
            }
    }
}
