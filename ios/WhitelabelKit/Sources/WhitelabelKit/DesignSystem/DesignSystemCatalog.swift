import SwiftUI

/**
 * Every component in both packs, light or dark: the check that a pack looks right before an app
 * adopts it (compare Gallery with Munch's `docs/design/design.html`). Gallery uses the sample brand
 * assets in `Resources/Preview` (Munch's colors, Instrument Serif and Geist); Material uses the
 * app's own brand colors. Opened from an app's Debug build.
 */
public struct DesignSystemCatalog: View {
    @State private var dark: Bool

    public init(dark: Bool = false) { _dark = State(initialValue: dark) }

    private let styles = [PreviewStyles.gallery, PreviewStyles.material]

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    Toggle("Dark", isOn: $dark)
                    ForEach(styles, id: \.pack.name) { style in
                        PackSection(style: style).appStyle(style)
                    }
                }
                .padding()
            }
            .navigationTitle("Design system")
        }
        .preferredColorScheme(dark ? .dark : .light)
    }
}

private struct PackSection: View {
    let style: AppStyle
    @State private var email = "ada@example.com"
    @State private var password = "short"
    @State private var notifications = true

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(style.pack.name).font(style.typography.headline)
            Text("Display").font(style.typography.display)
            Text("Title").font(style.typography.title)
            Text("Body text that wraps onto a second line to show the measure of the pack.").font(style.typography.body)
            AppCard {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text("Card").font(style.typography.title)
                    Text("A container on the background.").font(style.typography.body)
                }
            }
            Button("Primary action") {}.buttonStyle(PrimaryButtonStyle())
            Button("Secondary action") {}.buttonStyle(SecondaryButtonStyle())
            Button("Text action") {}.buttonStyle(TextButtonStyle())
            AppTextField("Email", text: $email)
            AppTextField("Password", text: $password, error: "Use at least 10 characters.", isSecure: true)
            AppValueRow("Theme", value: "System") {}
            AppToggleRow("Push notifications", isOn: $notifications)
            EmptyStateView(title: "No items yet", message: "Add your first one to see it here.").frame(height: 140)
            ErrorStateView(message: "No internet connection. Check your connection and try again.") {}.frame(height: 140)
        }
        .padding(style.pack.sizing.screenGutter)
        .background(style.colors.background)
    }
}

#Preview { DesignSystemCatalog() }
