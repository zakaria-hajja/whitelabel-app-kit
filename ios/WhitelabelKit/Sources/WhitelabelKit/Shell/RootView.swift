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
        root
            .appStyle(AppStyle(pack: runtime.definition.style))
            .preferredColorScheme(colorScheme(state.theme))
            .environment(runtime.navigator)
            .environment(\.appBrand, runtime.brand)
            .onOpenURL { runtime.open($0) }
            .onAppear { runtime.processPendingLink() }
            .onChange(of: linkTrigger) { old, new in
                // With a gate, the session switching means another user: start them clean.
                if runtime.definition.gate != nil, old.signedIn != new.signedIn { runtime.navigator.reset() }
                runtime.processPendingLink()
            }
            #if DEBUG
            .overlay(alignment: .topLeading) { DebugProbes(lastLink: runtime.lastLinkReceived, fakeLog: runtime.fakeLog) }
            #endif
    }

    /** What a pending link waits on: content showing, the session, a new link. */
    private struct LinkTrigger: Equatable {
        let showsContent: Bool
        let signedIn: Bool
        let links: Int
    }

    private var linkTrigger: LinkTrigger {
        LinkTrigger(showsContent: state.showsContent(hasGate: runtime.definition.gate != nil), signedIn: signedIn, links: runtime.linksReceived)
    }

    /** Without a content gate, today's [content]; with one, the splash, the lock or that content. */
    @ViewBuilder private var root: some View {
        if let driver = runtime.gateDriver {
            gated(driver)
        } else {
            content
        }
    }

    /**
     * The lock replaces the content, so nothing of the app (its screens, banners, `gatherConsent`)
     * runs while locked. A pending link is re-checked when the screen changes (unlock).
     */
    @ViewBuilder private func gated(_ driver: ContentGateDriver) -> some View {
        let screen = driver.screen(showsContent: state.showsContent(hasGate: runtime.definition.gate != nil))
        Group {
            switch screen {
            case .splash: SplashView()
            case .lock: runtime.definition.lockScreen?() ?? AnyView(SplashView())
            case .content: content
            }
        }
        .onChange(of: screen) { runtime.processPendingLink() }
    }

    @ViewBuilder private var content: some View {
        let hasGate = runtime.definition.gate != nil
        if !state.showsContent(hasGate: hasGate) {
            SplashView()
        } else if hasGate, !signedIn, let gate = runtime.definition.gate?.gateRoot() {
            GateView(root: gate, definition: runtime.definition, navigator: runtime.navigator)
                .task { runtime.gatherConsent() }
        } else {
            TabsView(definition: runtime.definition, routes: state.visibleTabRoutes, navigator: runtime.navigator)
                .task { runtime.gatherConsent() }
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

/** The gate's screens while signed out, on their own stack (a reset link opens over sign-in). */
private struct GateView: View {
    let root: AnyView
    let definition: AppDefinition
    @Bindable var navigator: Navigator

    var body: some View {
        NavigationStack(path: $navigator.gatePath) {
            root.navigationDestination(for: Destination.self) { destination in
                definition.view(for: destination) ?? AnyView(EmptyView())
            }
        }
        .onAppear { navigator.isGateShowing = true }
        .onDisappear { navigator.isGateShowing = false }
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
@Observable
final class AppRuntime {
    @ObservationIgnored private static var current: AppRuntime?

    static var isUnitTestHost: Bool { ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil }

    let definition: AppDefinition
    let brand: BrandConfig
    let shellViewModel: ShellViewModel
    let shell: SharedModel<ShellState, KotlinUnit>
    /** The app's lock, driven by the app lifecycle; nil for an app without one (today's shell). */
    let gateDriver: ContentGateDriver?
    @ObservationIgnored private var gateObservation: Task<Void, Never>?
    let navigator = Navigator()
    /** Counts incoming links, so the shell re-checks the pending one. */
    private(set) var linksReceived = 0
    /** Debug: the last link that reached the app, and the fake's recent requests (`_fake/log`), for UI tests. */
    private(set) var lastLinkReceived = ""
    private(set) var fakeLog = ""

    private init(definition: AppDefinition, brand: BrandConfig, shellViewModel: ShellViewModel, gate: ContentGate?) {
        self.definition = definition
        self.brand = brand
        self.shellViewModel = shellViewModel
        shell = SharedModel(holder: ViewModelHolder(viewModel: shellViewModel), state: shellViewModel.state, onAction: { _ in })
        guard let gate else {
            gateDriver = nil
            return
        }
        let graph = SharedGraph.shared
        let driver = ContentGateDriver(
            read: { graph.contentGateState(gate: gate) },
            foreground: { gate.onAppForeground() },
            background: { gate.onAppBackground() }
        )
        gateDriver = driver
        // Changes the gate makes on its own: the first read (PENDING → decided) and unlock.
        gateObservation = Task { [weak driver] in
            for await _ in graph.contentGateChanges(gate: gate) {
                driver?.refresh()
            }
        }
    }

    /** Starts the app once; an invalid definition stops it at launch with the rule it breaks, as on Android. */
    static func launch(_ definition: AppDefinition) -> AppRuntime {
        if let current { return current }
        if let problem = definition.validationError { fatalError("Invalid app definition: \(problem)") }
        let platform = PlatformFactory.make(bundle: .main)
        #if DEBUG
        let debugBuild = true
        #else
        let debugBuild = false
        #endif
        if let problem = definition.serviceProblem(firebaseMode: platform.firebase, debugBuild: debugBuild) { fatalError("Invalid app definition: \(problem)") }
        var useMockApi = false
        var mode = platform.firebase
        #if DEBUG
        let debug = DebugControls(arguments: ProcessInfo.processInfo.arguments)
        debug.beforeStart(bundle: .main)
        useMockApi = debug.useMockApi
        if useMockApi { IosMock.shared.enable() }
        if debug.fakeFirebase, !definition.services(.firebase).isEmpty { mode = "fake" }
        #endif
        do {
            try start(definition, platform: platform, useMockApi: useMockApi, mode: mode)
        } catch {
            fatalError("The app can't start: \(error.localizedDescription)")
        }
        // After start: the gate is a Koin binding, so the graph must be up to look for it.
        let gate = SharedGraph.shared.contentGateOrNull()
        if let problem = definition.contentGateProblem(bindsGate: gate != nil) { fatalError("Invalid app definition: \(problem)") }
        #if DEBUG
        if useMockApi { debug.afterStart(IosMock.shared.fakeBackend()) }
        #endif
        let runtime = AppRuntime(definition: definition, brand: platform.brand, shellViewModel: SharedGraph.shared.shellViewModel(), gate: gate)
        current = runtime
        // A tapped reminder opens its link like any other incoming link.
        ReminderNotifications.shared.install()
        ReminderNotifications.shared.connect { [weak runtime] in runtime?.open($0) }
        return runtime
    }

    /**
     * Installs the app's services, then starts the graph: a service hands Kotlin its ports before they
     * are needed. Firebase services get [mode] (`WL_FIREBASE`, or `fake`), ads services `WL_ADS`.
     */
    static func start(_ definition: AppDefinition, platform: IosPlatform, useMockApi: Bool, mode: String) throws {
        let firebase = definition.services(.firebase)
        if !firebase.isEmpty {
            SharedGraph.shared.declareFirebase()
            for service in firebase { service.install(mode: mode) }
        }
        if !definition.ads.isEmpty {
            SharedGraph.shared.declareAds(placements: definition.ads, gateRoutes: gateRoutes(of: definition))
        }
        for service in definition.services(.ads) { service.install(mode: platform.ads) }
        try SharedGraph.shared.start(platform: platform, definition: definition.spec, appModules: definition.modules, useMockApi: useMockApi)
    }

    /**
     * The placements' routes that the sign-in gate's screens own, so a placement there fails at
     * launch as on Android ("No ads under auth: …"); Kotlin checks the rest.
     */
    static func gateRoutes(of definition: AppDefinition) -> [String] {
        guard let gate = definition.gate else { return [] }
        return definition.ads.compactMap { $0.underTab as? String }.filter { gate.routes($0, [:]) != nil }
    }

    /**
     * Once per launch, after the first content shows (never from the splash), and only for an app
     * that has a consent to gather (a Firebase service or ads).
     */
    func gatherConsent() {
        SharedGraph.shared.consentOrNull()?.gatherOnLaunch()
    }

    private var signedIn: Bool { shell.state.session is SessionStateSignedIn }

    /** An incoming link is kept in the shell model (as on Android) and decided when the shell can. */
    func open(_ url: URL) {
        #if DEBUG
        lastLinkReceived = url.absoluteString
        #endif
        if case let .control(command) = DeepLinks.route(url, scheme: brand.deepLinkScheme, features: [], enabled: [], signedIn: false) {
            #if DEBUG
            if command == "log" { fakeLog = DebugControls.recentRequests() } else { DebugControls.run(command) }
            #else
            _ = command
            #endif
            return
        }
        shellViewModel.onDeepLink(link: url.absoluteString)
        linksReceived += 1
    }

    /** Opens, keeps (until sign-in) or drops the pending link, per the shared policy, once content shows. */
    func processPendingLink() {
        guard let link = shellViewModel.pendingDeepLink.value as? String else { return }
        guard let url = URL(string: link) else { return shellViewModel.onDeepLinkConsumed() }
        let state = shell.state
        let showsContent = state.showsContent(hasGate: definition.gate != nil)
        // Behind a lock the link waits until the content shows (unlock).
        let decision = DeepLinks.decide(showsContent: gateDriver.map { $0.screen(showsContent: showsContent) == .content } ?? showsContent) {
            DeepLinks.route(url, scheme: brand.deepLinkScheme, features: definition.features, enabled: Set(state.enabledFeatureIds), signedIn: signedIn)
        }
        guard case let .consume(routing) = decision else { return }
        shellViewModel.onDeepLinkConsumed()
        if case let .open(destination, tab, inGate) = routing {
            navigator.show(destination, tab: tab, inGate: inGate, visibleTabs: state.visibleTabRoutes)
        }
    }
}
