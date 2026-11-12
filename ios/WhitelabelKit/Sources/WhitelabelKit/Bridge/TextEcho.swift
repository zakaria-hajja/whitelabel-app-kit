import SwiftUI

/**
 * Text typed into a field goes to the shared ViewModel, and its state comes back a moment later.
 * Using that state as the field's value would drop what was typed meanwhile, so the field keeps
 * its own text, sends each change, and adopts the state's text only when the ViewModel changed it
 * itself (not an echo of something typed).
 */
struct TextEcho {
    private var sent: [String] = []

    /** The user typed [text] (and it was sent). */
    mutating func typed(_ text: String) {
        sent.append(text)
    }

    /** The state's text arrived: nil for an echo of something typed, else the text to adopt. */
    mutating func received(_ text: String) -> String? {
        if let index = sent.firstIndex(of: text) {
            sent.removeFirst(index + 1)
            return nil
        }
        sent.removeAll()
        return text
    }
}

/** A field's text kept locally, in step with a shared state's text (see [TextEcho]). */
struct BufferedText: ViewModifier {
    let value: String
    @Binding var local: String
    let send: (String) -> Void
    @State private var echo = TextEcho()

    func body(content: Content) -> some View {
        content
            .onAppear { local = value }
            .onChange(of: local) {
                guard local != value else { return }
                echo.typed(local)
                send(local)
            }
            .onChange(of: value) {
                if let adopted = echo.received(value), adopted != local { local = adopted }
            }
    }
}

extension View {
    /** Keeps [local] (the field's text) in step with the shared state's [value]; typing calls [send]. */
    func bufferedText(_ value: String, local: Binding<String>, send: @escaping (String) -> Void) -> some View {
        modifier(BufferedText(value: value, local: local, send: send))
    }
}
