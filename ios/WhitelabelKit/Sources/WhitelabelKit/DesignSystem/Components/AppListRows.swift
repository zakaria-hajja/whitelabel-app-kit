import SwiftUI

/** A setting and its current value on one row; tapping opens its choices. */
public struct AppValueRow: View {
    @Environment(\.appStyle) private var style
    private let label: String
    private let value: String
    private let action: () -> Void

    public init(_ label: String, value: String, action: @escaping () -> Void) {
        self.label = label
        self.value = value
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack {
                Text(label).font(style.typography.body).foregroundStyle(style.colors.onSurface)
                Spacer(minLength: Spacing.md)
                Text(value).font(style.typography.body).foregroundStyle(style.colors.onSurfaceVariant)
                Image(systemName: "chevron.right").font(.footnote).foregroundStyle(style.colors.onSurfaceVariant).accessibilityHidden(true)
            }
            .frame(minHeight: style.pack.sizing.listRowMinHeight)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityValue(value)
    }
}

/** An on/off setting: a native toggle, with the pack's toggle haptic. */
public struct AppToggleRow: View {
    @Environment(\.appStyle) private var style
    private let label: String
    @Binding private var isOn: Bool

    public init(_ label: String, isOn: Binding<Bool>) {
        self.label = label
        self._isOn = isOn
    }

    public var body: some View {
        Toggle(label, isOn: $isOn)
            .font(style.typography.body)
            .frame(minHeight: style.pack.sizing.listRowMinHeight)
            .sensoryFeedback(.selection, trigger: isOn) { _, _ in style.pack.haptics[.toggle] != nil }
    }
}

/** A sheet to pick one value of a setting (theme, language), with the selected one checked. */
public struct AppChoiceSheet<Choice: Hashable>: View {
    @Environment(\.appStyle) private var style
    @Environment(\.dismiss) private var dismiss
    private let title: String
    private let choices: [Choice]
    private let selected: Choice?
    private let label: (Choice) -> String
    private let onSelect: (Choice) -> Void

    public init(title: String, choices: [Choice], selected: Choice?, label: @escaping (Choice) -> String, onSelect: @escaping (Choice) -> Void) {
        self.title = title
        self.choices = choices
        self.selected = selected
        self.label = label
        self.onSelect = onSelect
    }

    public var body: some View {
        NavigationStack {
            List(choices, id: \.self) { choice in
                Button {
                    onSelect(choice)
                    dismiss()
                } label: {
                    HStack {
                        Text(label(choice)).font(style.typography.body).foregroundStyle(style.colors.onSurface)
                        Spacer()
                        if choice == selected { Image(systemName: "checkmark").foregroundStyle(style.colors.primary) }
                    }
                }
                .accessibilityAddTraits(choice == selected ? .isSelected : [])
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }
}

#Preview("Rows") {
    ForEach([StylePack.material, .gallery], id: \.name) { pack in
        VStack(spacing: 0) {
            AppValueRow("Theme", value: "System") {}
            AppToggleRow("Push notifications", isOn: .constant(true))
        }
        .padding()
        .appStyle(AppStyle(pack: pack))
    }
}
