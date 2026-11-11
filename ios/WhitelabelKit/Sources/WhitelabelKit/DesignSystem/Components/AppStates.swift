import SwiftUI

/** Content is on its way. */
public struct LoadingView: View {
    public init() {}
    public var body: some View {
        ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity).accessibilityLabel(KitStrings.text("Loading"))
    }
}

/** Nothing to show yet, and what to do about it. */
public struct EmptyStateView: View {
    @Environment(\.appStyle) private var style
    private let title: String
    private let message: String

    public init(title: String, message: String) {
        self.title = title
        self.message = message
    }

    public var body: some View {
        VStack(spacing: Spacing.sm) {
            Text(title).font(style.typography.title)
            Text(message).font(style.typography.body).foregroundStyle(style.colors.onSurfaceVariant)
        }
        .multilineTextAlignment(.center)
        .padding(style.pack.sizing.screenGutter)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/** Something went wrong: the (shared) message and a way to try again. */
public struct ErrorStateView: View {
    @Environment(\.appStyle) private var style
    private let message: String
    private let onRetry: () -> Void

    public init(message: String, onRetry: @escaping () -> Void) {
        self.message = message
        self.onRetry = onRetry
    }

    public var body: some View {
        VStack(spacing: Spacing.md) {
            Text(message).font(style.typography.body).multilineTextAlignment(.center)
            Button(KitStrings.text("Try again"), action: onRetry).buttonStyle(TextButtonStyle())
        }
        .padding(style.pack.sizing.screenGutter)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

public extension View {
    /** A native confirmation for a destructive or important action (sign out, delete). */
    func appConfirm(_ title: String, isPresented: Binding<Bool>, confirmLabel: String, role: ButtonRole? = .destructive, message: String? = nil, onConfirm: @escaping () -> Void) -> some View {
        alert(title, isPresented: isPresented) {
            Button(confirmLabel, role: role, action: onConfirm)
            Button(KitStrings.text("Cancel"), role: .cancel) {}
        } message: {
            if let message { Text(message) }
        }
    }
}

#Preview("States") {
    ForEach([StylePack.material, .gallery], id: \.name) { pack in
        VStack {
            LoadingView().frame(height: 80)
            EmptyStateView(title: "No items yet", message: "Add your first one to see it here.").frame(height: 140)
            ErrorStateView(message: "No internet connection. Check your connection and try again.") {}.frame(height: 140)
        }
        .appStyle(AppStyle(pack: pack))
    }
}
