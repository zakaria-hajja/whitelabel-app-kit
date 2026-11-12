import Observation
import Shared
import SwiftUI

/**
 * What the privacy choices row needs, without any UI: [visible] follows `Consent.privacyOptionsRequired`
 * on the main actor, and [open] shows UMP's privacy options. With no consent (an app without a
 * Firebase service) it is never visible and does nothing.
 */
@MainActor
@Observable
public final class PrivacyChoicesModel {
    public private(set) var visible: Bool

    @ObservationIgnored private let consent: Consent?
    @ObservationIgnored private var observation: Task<Void, Never>?

    public init(consent: Consent?) {
        self.consent = consent
        visible = consent?.privacyOptionsRequired.value.boolValue ?? false
        guard let flow = consent?.privacyOptionsRequired else { return }
        observation = Task { [weak self] in
            for await value in flow {
                guard let self else { return }
                self.visible = value.boolValue
            }
        }
    }

    public func open() {
        consent?.showPrivacyOptions()
    }

    deinit { observation?.cancel() }
}

/**
 * "Privacy choices": opens Google's privacy options form. It shows only while UMP says they are
 * required, and draws nothing in an app without a Firebase service. Place it in the app's own
 * settings section; the shared Settings screen doesn't include it.
 */
public struct PrivacyChoicesRow: View {
    @State private var model = PrivacyChoicesModel(consent: SharedGraph.shared.consentOrNull())

    public init() {}

    public var body: some View {
        if model.visible {
            AppValueRow(KitStrings.text("Privacy choices"), value: "") { model.open() }
        }
    }
}
