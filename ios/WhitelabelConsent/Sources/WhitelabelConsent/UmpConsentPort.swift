import UIKit
import Shared
import UserMessagingPlatform
import WhitelabelKit

/** UMP's consent status as the shared `UmpStatus`. */
func umpStatus(of status: ConsentStatus) -> UmpStatus {
    switch status {
    case .required: .required
    case .notRequired: .notRequired
    case .obtained: .obtained
    default: .unknown
    }
}

/**
 * The shared `ConsentPort` over Google's User Messaging Platform, the Swift twin of Android's
 * `UmpConsentPort`. UMP shows its form only where the law requires one (EEA, UK). The form is
 * presented from the key window's root view controller (the topmost one it presents); without one
 * `gather` calls back nil (a failed gather, retried next launch). `-consentEEA` (Debug) makes UMP
 * behave as in the EEA. UMP's behavior itself is checked on a device.
 */
public final class UmpConsentPort: NSObject, ConsentPort {
    private var info: ConsentInformation { ConsentInformation.shared }

    public func gather(onDone: @escaping (UmpResult?) -> Void) {
        guard Self.presenter() != nil else { return onDone(nil) }
        let parameters = RequestParameters()
        if LaunchFlags.consentEEA {
            let debug = DebugSettings()
            debug.geography = .EEA
            parameters.debugSettings = debug
        }
        info.requestConsentInfoUpdate(with: parameters) { error in
            // The window may have gone while the info update ran.
            guard error == nil, let presenter = Self.presenter() else { return onDone(nil) }
            ConsentForm.loadAndPresentIfRequired(from: presenter) { formError in
                onDone(formError == nil ? self.result() : nil)
            }
        }
    }

    public func privacyOptionsRequired() -> Bool {
        info.privacyOptionsRequirementStatus == .required
    }

    public func showPrivacyOptions(onDone: @escaping () -> Void) {
        guard let presenter = Self.presenter() else { return onDone() }
        ConsentForm.presentPrivacyOptionsForm(from: presenter) { _ in onDone() }
    }

    private func result() -> UmpResult {
        UmpResult(
            status: umpStatus(of: info.consentStatus),
            canRequestAds: info.canRequestAds,
            // The TCF purpose-consent string UMP writes to the standard defaults.
            purposeConsents: UserDefaults.standard.string(forKey: "IABTCF_PurposeConsents")
        )
    }

    /**
     * The key window's root view controller, or the topmost one it presents. The shell calls the
     * port on the main thread (consent is gathered from the first screen) and UMP calls back there.
     */
    private static func presenter() -> UIViewController? {
        MainActor.assumeIsolated {
            let window = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap(\.windows)
                .first(where: \.isKeyWindow)
            var top = window?.rootViewController
            while let presented = top?.presentedViewController { top = presented }
            return top
        }
    }
}
