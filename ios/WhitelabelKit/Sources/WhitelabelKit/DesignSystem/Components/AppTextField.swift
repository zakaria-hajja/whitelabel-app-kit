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
            // Visible above the field even once it's filled (design.html); VoiceOver reads it from the field.
            Text(label).font(style.typography.label).foregroundStyle(style.colors.onSurfaceVariant).accessibilityHidden(true)
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
            if let error {
                Text(error)
                    .font(style.typography.label)
                    .foregroundStyle(style.colors.error)
            }
        }
    }
}

#Preview("Text fields") {
    PackPreviews {
        AppTextField("Email", text: .constant(""))
        AppTextField("Email", text: .constant("ada@example.com"))
        AppTextField("Password", text: .constant("secret"), error: "Use at least 10 characters.", isSecure: true)
    }
}
