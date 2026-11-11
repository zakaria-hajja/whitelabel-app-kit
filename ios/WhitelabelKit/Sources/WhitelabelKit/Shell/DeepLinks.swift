import Foundation
import Shared

/** What to do with an incoming link. */
enum DeepLinkRouting: Equatable {
    /** Show [destination], in [tab] (the owning feature's tab) when it has one, or on the gate's stack. */
    case open(Destination, tab: String?, inGate: Bool)
    /** Keep it until sign-in. */
    case hold
    case ignore
    /** A Debug control link, `<scheme>://_fake/<command>`. */
    case control(String)
}

/** What to do with the pending link now. */
enum PendingLinkDecision: Equatable {
    /** Leave it pending: the shell isn't showing content yet, or it waits for sign-in. */
    case wait
    /** Drop it from the pending slot, then act on the routing. */
    case consume(DeepLinkRouting)
}

/**
 * Finds the feature that owns a link; the shared policy (`deepLinkAction`) decides whether it opens,
 * waits for sign-in or is dropped, so both platforms treat links alike.
 */
enum DeepLinks {
    /**
     * As Android's shell: every link is kept pending first and decided only once the shell shows
     * content (tabs decided and, with a gate, the session known), so a cold-start link is neither
     * lost before the tabs exist nor judged against an unknown session.
     */
    static func decide(showsContent: Bool, routing: () -> DeepLinkRouting) -> PendingLinkDecision {
        guard showsContent else { return .wait }
        let routing = routing()
        return routing == .hold ? .wait : .consume(routing)
    }

    /** [enabled] are the ids of the features that are on: a feature its flag switched off owns no links. */
    static func route(_ url: URL, scheme: String, features: [any Feature], enabled: Set<String>, signedIn: Bool) -> DeepLinkRouting {
        guard let linkScheme = url.scheme, linkScheme.caseInsensitiveCompare(scheme) == .orderedSame else { return .ignore }
        if url.host == "_fake" { return .control(url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))) }
        var owner: (feature: any Feature, destination: Destination)?
        for feature in features where enabled.contains(feature.spec.id) {
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
            return .open(owner.destination, tab: owner.feature.spec.tabRoute, inGate: owner.feature.spec.isGate)
        case .hold: return .hold
        case .ignore: return .ignore
        }
    }
}
