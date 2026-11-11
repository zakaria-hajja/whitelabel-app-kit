import XCTest
import Shared
@testable import WhitelabelKit

@MainActor
final class SharedModelTests: XCTestCase {

    override func setUp() async throws { try TestGraph.start() }
    override func tearDown() async throws { TestGraph.stop() }

    func testUpdatesStateAndForwardsActions() async throws {
        let viewModel = SharedGraph.shared.settingsViewModel()
        let model = SharedModel(holder: ViewModelHolder(viewModel: viewModel), state: viewModel.state, onAction: viewModel.onAction)
        model.send(SettingsActionOnThemeSelect(theme: .dark))
        try await waitUntil { model.state.theme == .dark }
    }

    func testClearsTheViewModelWhenReleased() async throws {
        let viewModel = SharedGraph.shared.settingsViewModel()
        let holder = ViewModelHolder(viewModel: viewModel)
        var model: SharedModel<SettingsState, SettingsAction>? = SharedModel(holder: holder, state: viewModel.state, onAction: viewModel.onAction)
        XCTAssertNotNil(model)
        model = nil
        XCTAssertTrue(holder.isCleared)
    }
}

/** Polls [condition] on the main actor until it holds, failing after [timeout] seconds. */
@MainActor
func waitUntil(timeout: TimeInterval = 5, _ condition: @MainActor () -> Bool) async throws {
    let deadline = Date().addingTimeInterval(timeout)
    while !condition() {
        if Date() > deadline { XCTFail("condition not met within \(timeout)s"); return }
        try await Task.sleep(nanoseconds: 20_000_000)
    }
}
