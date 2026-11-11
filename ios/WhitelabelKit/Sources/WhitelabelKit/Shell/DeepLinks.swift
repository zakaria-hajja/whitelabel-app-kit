import Foundation
import Shared

/** What to do with an incoming link. */
enum DeepLinkRouting: Equatable {
    /** Show [destination], in [tab] (the owning feature's tab) when it has one. */
    case open(Destination, tab: String?)
    /** Keep it until sign-in. */
    case hold
    case ignore
    /** A Debug control link, `<scheme>://_fake/<command>`. */
    case control(String)
}

/**
 * Finds the feature that owns a link; the shared policy (`deepLinkAction`) decides whether it opens,
 * waits for sign-in or is dropped, so both platforms treat links alike.
 */
enum DeepLinks {
    static func route(_ url: URL, scheme: String, features: [any Feature], signedIn: Bool) -> DeepLinkRouting {
        guard let linkScheme = url.scheme, linkScheme.caseInsensitiveCompare(scheme) == .orderedSame else { return .ignore }
        if url.host == "_fake" { return .control(url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))) }
        var owner: (feature: any Feature, destination: Destination)?
        for feature in features {
            if let destination = feature.destination(forLink: url) {
                owner = (feature, destination)
                break
            }
        }
        let action = DeepLinkPolicyKt.deepLinkAction(
            inMain: owner.map { !$0.feature.spec.isGate } ?? false,
            inAuth: owner.map { $0.feature.spec.isGate } ?? false,
            signedIn: signedIn,
            hasGate: features.contains { $0.spec.isGate }
        )
        switch action {
        case .open:
            guard let owner else { return .ignore }
            return .open(owner.destination, tab: owner.feature.spec.tabRoute)
        case .hold: return .hold
        case .ignore: return .ignore
        }
    }
}
