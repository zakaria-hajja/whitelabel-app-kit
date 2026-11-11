import SwiftUI
import Shared
import WhitelabelKit

/** Temporary (Part B): proves the slice — the shared graph starts and a shared message resolves. */
@main struct ShowcaseApp: App {
    @State private var message = ""

    var body: some Scene {
        WindowGroup {
            Text(message)
                .task {
                    message = await resolve(DataErrorTextKt.toUiText(DataErrorNetwork.noInternet.toKotlinEnum()))
                }
        }
    }
}
