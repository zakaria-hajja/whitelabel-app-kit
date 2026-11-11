import SwiftUI
import Shared

/**
 * The app's root, as Android's `FoundationActivity`: starts the shared graph once, shows a splash
 * until the shared shell model says the app may show, then the gate (signed out) or the tabs. It
 * applies the style pack and the theme, and routes incoming links.
 *
 *     @main struct MyApp: App {
 *         var body: some Scene { WindowGroup { RootView(definition: AppDefinition(style: .gallery) { Settings() }) } }
 *     }
 */
public struct RootView: View {
    private let runtime: AppRuntime?

    public init(definition: AppDefinition) {
        // As the host of unit tests the app stays empty: the tests start the graph their own way.
        runtime = AppRuntime.isUnitTestHost ? nil : AppRuntime.launch(definition)
    }

    public var body: some View {
        if let runtime { ShellContent(runtime: runtime) }
    }
}

private struct ShellContent: View {
    let runtime: AppRuntime

    private var state: ShellState { runtime.shell.state }
    private var signedIn: Bool { state.session is SessionStateSignedIn }

    var body: some View {
        content
            .appStyle(AppStyle(pack: runtime.definition.style))
            .preferredColorScheme(colorScheme(state.theme))
            .environment(runtime.navigator)
            .onOpenURL { runtime.open($0) }
            .onChange(of: signedIn) { runtime.openHeldLink() }
            #if DEBUG
            .overlay(alignment: .topLeading) { ColorSchemeProbe() }
            #endif
    }

    @ViewBuilder private var content: some View {
        let hasGate = runtime.definition.gate != nil
        if !state.showsContent(hasGate: hasGate) {
            SplashView()
        } else if hasGate, !signedIn, let gate = runtime.definition.gate?.gateRoot() {
            gate
        } else {
            TabsView(definition: runtime.definition, routes: state.visibleTabRoutes, navigator: runtime.navigator)
        }
    }

    private func colorScheme(_ theme: ThemePreference) -> ColorScheme? {
        switch theme {
        case .light: .light
        case .dark: .dark
        default: nil
        }
    }
}

/** The app's background with a spinner while the shell decides its tabs (at most the config wait). */
private struct SplashView: View {
    @SwiftUI.Environment(\.appStyle) private var style

    var body: some View {
        ZStack {
            style.colors.background.ignoresSafeArea()
            ProgressView()
        }
        .accessibilityIdentifier("splash")
    }
}

/**
 * One per process: the started graph, the shell's model and the navigation state. SwiftUI may
 * create `RootView` more than once; the graph and the shell model must not be.
 */
@MainActor
final class AppRuntime {
    private static var current: AppRuntime?

    static var isUnitTestHost: Bool { ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil }

    let definition: AppDefinition
    let brand: BrandConfig
    let shellViewModel: ShellViewModel
    let shell: SharedModel<ShellState, KotlinUnit>
    let navigator = Navigator()

    private init(definition: AppDefinition, brand: BrandConfig, shellViewModel: ShellViewModel) {
        self.definition = definition
        self.brand = brand
        self.shellViewModel = shellViewModel
        shell = SharedModel(holder: ViewModelHolder(viewModel: shellViewModel), state: shellViewModel.state, onAction: { _ in })
    }

    /** Starts the app once; an invalid definition stops it at launch with the rule it breaks, as on Android. */
    static func launch(_ definition: AppDefinition) -> AppRuntime {
        if let current { return current }
        if let problem = definition.validationError { fatalError("Invalid app definition: \(problem)") }
        let platform = PlatformFactory.make(bundle: .main)
        var useMockApi = false
        #if DEBUG
        let debug = DebugControls(arguments: ProcessInfo.processInfo.arguments)
        debug.beforeStart(bundle: .main)
        useMockApi = debug.useMockApi
        if useMockApi { IosMock.shared.enable() }
        #endif
        do {
            try SharedGraph.shared.start(platform: platform, definition: definition.spec, appModules: definition.modules, useMockApi: useMockApi)
        } catch {
            fatalError("The app can't start: \(error.localizedDescription)")
        }
        #if DEBUG
        if useMockApi { debug.afterStart(IosMock.shared.fakeBackend()) }
        #endif
        let runtime = AppRuntime(definition: definition, brand: platform.brand, shellViewModel: SharedGraph.shared.shellViewModel())
        current = runtime
        return runtime
    }

    private var signedIn: Bool { shell.state.session is SessionStateSignedIn }

    func open(_ url: URL) {
        switch DeepLinks.route(url, scheme: brand.deepLinkScheme, features: definition.features, signedIn: signedIn) {
        case let .open(destination, tab):
            show(destination, in: tab)
        case .hold:
            shellViewModel.onDeepLink(link: url.absoluteString)
        case .ignore:
            break
        case let .control(command):
            #if DEBUG
            DebugControls.run(command)
            #else
            _ = command
            #endif
        }
    }

    /** After sign-in, opens the link that was waiting for it. */
    func openHeldLink() {
        guard signedIn, let link = shellViewModel.pendingDeepLink.value as? String else { return }
        shellViewModel.onDeepLinkConsumed()
        if let url = URL(string: link) { open(url) }
    }

    private func show(_ destination: Destination, in tab: String?) {
        let visible = shell.state.visibleTabRoutes
        if visible.contains(destination.route) {
            navigator.selectedTab = destination.route
            return
        }
        if let tab, visible.contains(tab) { navigator.selectedTab = tab }
        navigator.push(destination)
    }
}
