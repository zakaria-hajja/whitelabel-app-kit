import SwiftUI
import Shared
import WhitelabelKit

/**
 * Showcase on iOS. Until the shell arrives (Part D) it shows a shared message, proving the shared
 * strings ship; in Debug, `-designCatalog` opens the design-system catalog instead.
 */
@main struct ShowcaseApp: App {
    @State private var message = ""

    var body: some Scene {
        WindowGroup {
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("-designCatalog") {
                DesignSystemCatalog(dark: ProcessInfo.processInfo.arguments.contains("-catalogDark"))
            } else {
                proof
            }
            #else
            proof
            #endif
        }
    }

    private var proof: some View {
        Text(message).task { message = await resolve(DataErrorTextKt.toUiText(DataErrorNetwork.noInternet.toKotlinEnum())) }
    }
}
