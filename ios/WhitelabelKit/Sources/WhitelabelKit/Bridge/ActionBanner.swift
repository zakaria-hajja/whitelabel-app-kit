import SwiftUI

/**
 * A one-off message with at most one action, for events a screen handles itself ("Fed · +20 pts ·
 * Undo"). `messageBanner` shows a ViewModel's message from state; this one is set by the screen.
 */
public struct BannerItem: Identifiable, Equatable {
    public let id = UUID()
    public let text: String
    public let actionTitle: String?
    public let action: (@MainActor () -> Void)?
    /** The action is the only way back (a one-tap Delete's Undo): a later banner never knocks it out. */
    public let protect: Bool

    public init(text: String, actionTitle: String? = nil, protect: Bool = false, action: (@MainActor () -> Void)? = nil) {
        self.text = text
        self.actionTitle = actionTitle
        self.protect = protect
        self.action = action
    }

    public static func == (lhs: BannerItem, rhs: BannerItem) -> Bool { lhs.id == rhs.id }
}

public extension View {
    /**
     * Shows `item` at the bottom, then sets it back to nil: with an action for 10 s (Android's long
     * snackbar), without one for `MessageBanner.seconds` (the short one). Its action button runs the
     * action and hides the banner. One at a time, never a long chain (`BannerQueue`): a newer item
     * replaces it, unless it's `protect`ed. While it shows, `item` is the banner on screen. Leaving
     * the screen (a tab switch, a push) hides it and uses it up, so it never shows again on return.
     */
    func actionBanner(_ item: Binding<BannerItem?>) -> some View {
        modifier(ActionBannerModifier(item: item))
    }
}

/**
 * Which banner shows, as Munch's Android `Notices`: a newer one replaces the one on screen, so a burst
 * never chains. A `protect`ed one keeps its place until it ends; later protected ones queue behind it
 * in order, and a later ordinary one waits in a single slot (the newest wins) to show after.
 */
struct BannerQueue {
    private(set) var current: BannerItem?
    private var protectedWaiting: [BannerItem] = []
    private var waiting: BannerItem?

    /** The banner to show now, or nil when `item` has to wait for the protected one on screen. */
    mutating func offer(_ item: BannerItem) -> BannerItem? {
        guard current?.protect == true else {
            current = item
            return item
        }
        if item.protect { protectedWaiting.append(item) } else { waiting = item }
        return nil
    }

    /** The one on screen ended (timed out or acted on): the next to show, if any. */
    mutating func finished() -> BannerItem? {
        if protectedWaiting.isEmpty {
            current = waiting
            waiting = nil
        } else {
            current = protectedWaiting.removeFirst()
        }
        return current
    }
}

enum ActionBanner {
    /** Android's long snackbar (Munch's `Notices` uses the same): an action (Undo) needs time to reach, with VoiceOver too. */
    static let actionSeconds = 10.0

    /**
     * With an action, Material's long 10 s, with or without VoiceOver; without one, `seconds`. A longer
     * `seconds` (Debug `-messageSeconds`) still wins.
     */
    static func duration(seconds: Double, hasAction: Bool) -> Double {
        hasAction ? max(seconds, actionSeconds) : seconds
    }

    /** Shows, announces, waits, hides. A cancelled run (a newer item, or the action) leaves the banner to its successor. */
    @MainActor
    static func present(_ item: BannerItem, show: (BannerItem?) -> Void, seconds: Double) async {
        show(item)
        UIAccessibility.post(notification: .announcement, argument: item.text)
        do {
            try await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
        } catch {
            return
        }
        show(nil)
    }
}

private struct ActionBannerModifier: ViewModifier {
    @Binding var item: BannerItem?
    @State private var shown: BannerItem?
    @State private var queue = BannerQueue()
    @SwiftUI.Environment(\.appStyle) private var style

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let shown { banner(shown).transition(.move(edge: .bottom).combined(with: .opacity)) }
            }
            .onChange(of: item, initial: true) { _, new in
                guard let new, new != shown else { return }
                if let now = queue.offer(new) {
                    withAnimation { shown = now }
                } else {
                    // It waits: the screen's item stays the banner on screen.
                    item = shown
                }
            }
            .task(id: shown?.id) {
                guard let current = shown else { return }
                let seconds = ActionBanner.duration(seconds: MessageBanner.seconds, hasAction: current.action != nil)
                await ActionBanner.present(current, show: { value in if value == nil { advance() } }, seconds: seconds)
            }
            // The banner belongs to this screen. Leaving cancels the task above mid-wait, which would
            // leave it on screen and pending, to show again on return: hide it and use it up instead.
            .onDisappear {
                queue = BannerQueue()
                shown = nil
                if item != nil { item = nil }
            }
    }

    /** The one on screen ended: the next waiting banner, or none. */
    private func advance() {
        let next = queue.finished()
        withAnimation { shown = next }
        item = next
    }

    private func banner(_ item: BannerItem) -> some View {
        HStack(spacing: Spacing.md) {
            Text(item.text)
                .font(style.typography.body)
                .foregroundStyle(style.colors.surface)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityIdentifier("banner.text")
            if let title = item.actionTitle, let action = item.action {
                Button(title) {
                    action()
                    advance()
                }
                .font(style.typography.label)
                .foregroundStyle(style.colors.surface)
                .accessibilityIdentifier("banner.action")
            }
        }
        .padding(Spacing.md)
        .background(style.colors.onSurface, in: RoundedRectangle(cornerRadius: style.pack.corners.small))
        .padding(Spacing.md)
    }
}
