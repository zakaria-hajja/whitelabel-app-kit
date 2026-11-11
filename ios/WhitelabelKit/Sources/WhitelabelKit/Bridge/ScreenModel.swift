import Observation

/**
 * Holds a screen's [SharedModel], made on first use. SwiftUI evaluates a `@State` initial value
 * every time the view struct is rebuilt (and drops all but the first), so building the shared
 * ViewModel there would create and clear one per rebuild; this box makes it once.
 *
 *     @State private var screen = ScreenModel { SettingsScreen.makeModel() }
 *     ... screen.model.state ...
 */
@MainActor
public final class ScreenModel<State: AnyObject, Action: AnyObject> {
    private let make: () -> SharedModel<State, Action>
    private var made: SharedModel<State, Action>?

    public init(_ make: @escaping () -> SharedModel<State, Action>) {
        self.make = make
    }

    public var model: SharedModel<State, Action> {
        if let made { return made }
        let model = make()
        made = model
        return model
    }
}
