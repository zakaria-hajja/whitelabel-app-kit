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

    public init(text: String, actionTitle: String? = nil, action: (@MainActor () -> Void)? = nil) {
        self.text = text
        self.actionTitle = actionTitle
        self.action = action
    }

    public static func == (lhs: BannerItem, rhs: BannerItem) -> Bool { lhs.id == rhs.id }
}

public extension View {
    /**
     * Shows `item` at the bottom for `MessageBanner.seconds` (Android's short snackbar), then sets it
     * back to nil. Its action button runs the action and hides the banner. A newer item replaces it.
     */
    func actionBanner(_ item: Binding<BannerItem?>) -> some View {
        modifier(ActionBannerModifier(item: item))
    }
}

enum ActionBanner {
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
    @SwiftUI.Environment(\.appStyle) private var style

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let shown { banner(shown).transition(.move(edge: .bottom).combined(with: .opacity)) }
            }
            .task(id: item?.id) {
                guard let item else { return }
                await ActionBanner.present(item, show: { value in withAnimation { shown = value } }, seconds: MessageBanner.seconds)
                if shown == nil, self.item == item { self.item = nil }
            }
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
                    withAnimation { shown = nil }
                    if self.item == item { self.item = nil }
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
