import SwiftUI
import Shared

/** Resolves a shared message (a ViewModel's `UiText`) in the device language. */
public func resolve(_ text: UiText) async -> String {
    (try? await Texts.shared.resolve(text: text)) ?? ""
}

/** Shows a shared message; keeps the previous text while a new one resolves, so nothing flickers. */
public struct UiTextView: View {
    private let text: UiText
    @State private var resolved = ""

    public init(_ text: UiText) { self.text = text }

    public var body: some View {
        Text(resolved)
            .task(id: ObjectIdentifier(text)) { resolved = await resolve(text) }
    }
}
