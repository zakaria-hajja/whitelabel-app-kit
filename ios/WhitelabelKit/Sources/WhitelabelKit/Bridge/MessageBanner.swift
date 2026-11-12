import SwiftUI
import Shared

public extension View {
    /**
     * Shows a ViewModel's one-off message at the bottom for a few seconds, then reports it shown
     * (Android's snackbar). A newer message replaces it.
     */
    func messageBanner(_ message: UiText?, onShown: @escaping () -> Void) -> some View {
        modifier(MessageBannerModifier(message: message, onShown: onShown))
    }
}

enum MessageBanner {
    /** As Android's short snackbar; Debug `-messageSeconds` lengthens it for UI tests. */
    static var seconds = 4.0

    /**
     * Resolves, shows, waits, hides, then reports shown. A cancelled run (a newer message arrived)
     * stops without reporting: reporting would clear the newer message in the ViewModel.
     */
    @MainActor
    static func present(resolve: () async -> String, show: (String?) -> Void, seconds: Double, onShown: () -> Void) async {
        let text = await resolve()
        guard !Task.isCancelled else { return }
        show(text)
        UIAccessibility.post(notification: .announcement, argument: text)
        do {
            try await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
        } catch {
            return
        }
        show(nil)
        onShown()
    }
}

private struct MessageBannerModifier: ViewModifier {
    let message: UiText?
    let onShown: () -> Void
    @State private var text: String?
    @SwiftUI.Environment(\.appStyle) private var style

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let text {
                    Text(text)
                        .font(style.typography.body)
                        .foregroundStyle(style.colors.surface)
                        .padding(Spacing.md)
                        .background(style.colors.onSurface, in: RoundedRectangle(cornerRadius: style.pack.corners.small))
                        .padding(Spacing.md)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .task(id: message.map(ObjectIdentifier.init)) {
                guard let message else { return }
                await MessageBanner.present(
                    resolve: { await resolve(message) },
                    show: { value in withAnimation { text = value } },
                    seconds: MessageBanner.seconds,
                    onShown: onShown
                )
            }
    }
}
