import Observation
import Shared

/**
 * A shared Kotlin ViewModel as something SwiftUI observes: [state] follows the ViewModel's
 * StateFlow on the main actor, [send] forwards an action, and releasing the model clears the
 * ViewModel (its holder), as leaving a screen does on Android. Swift never computes state itself.
 */
@MainActor
@Observable
public final class SharedModel<State: AnyObject, Action: AnyObject> {
    public private(set) var state: State

    @ObservationIgnored private let holder: ViewModelHolder
    @ObservationIgnored private let onAction: (Action) -> Void
    @ObservationIgnored private var observation: Task<Void, Never>?

    public init(holder: ViewModelHolder, state flow: SkieSwiftStateFlow<State>, onAction: @escaping (Action) -> Void) {
        self.holder = holder
        self.state = flow.value
        self.onAction = onAction
        observation = Task { [weak self] in
            for await value in flow {
                guard let self else { return }
                self.state = value
            }
        }
    }

    public func send(_ action: Action) {
        onAction(action)
    }

    deinit {
        observation?.cancel()
        holder.clear()
    }
}
