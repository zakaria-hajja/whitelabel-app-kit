import SwiftUI

/** A native text field with its label and, when [error] is set, the error below it (read by VoiceOver). */
public struct AppTextField: View {
    @Environment(\.appStyle) private var style
    private let label: String
    @Binding private var text: String
    private let error: String?
    private let isSecure: Bool

    public init(_ label: String, text: Binding<String>, error: String? = nil, isSecure: Bool = false) {
        self.label = label
        self._text = text
        self.error = error
        self.isSecure = isSecure
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Group {
                if isSecure { SecureField(label, text: $text) } else { TextField(label, text: $text) }
            }
            .font(style.typography.body)
            .padding(.horizontal, Spacing.md)
            .frame(minHeight: style.pack.sizing.controlHeight)
            .overlay(
                RoundedRectangle(cornerRadius: style.pack.corners.small)
                    .strokeBorder(error == nil ? style.colors.outline : style.colors.error, lineWidth: 1)
            )
            .accessibilityLabel(label)
            .accessibilityValue(error ?? "")
            if let error {
                Text(error)
                    .font(style.typography.label)
                    .foregroundStyle(style.colors.error)
                    .accessibilityHidden(true)
            }
        }
    }
}

#Preview("Text fields") {
    ForEach([StylePack.material, .gallery], id: \.name) { pack in
        VStack(spacing: Spacing.md) {
            AppTextField("Email", text: .constant("ada@example.com"))
            AppTextField("Password", text: .constant("secret"), error: "Use at least 10 characters.", isSecure: true)
        }
        .padding()
        .appStyle(AppStyle(pack: pack))
    }
}
