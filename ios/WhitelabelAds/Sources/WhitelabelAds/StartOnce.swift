import Foundation

/**
 * Runs a start once and lets every caller wait for it, as Android's `StartOnce`: the Google Mobile
 * Ads SDK starts on the first banner the rules let load, never at app startup. A start that throws
 * reports failure to its waiters and is forgotten, so a later banner tries again. That retry only
 * matters if a start can fail: `MobileAds.shared.start` doesn't throw today.
 */
@MainActor
final class StartOnce {
    static let mobileAds = StartOnce()

    private var current: Task<Bool, Never>?

    /** Starts with [begin] unless a start is running or done; true once started. */
    func run(_ begin: @escaping @MainActor () async throws -> Void) async -> Bool {
        if let current { return await current.value }
        let task = Task { @MainActor in
            do {
                try await begin()
                return true
            } catch {
                return false
            }
        }
        current = task
        let started = await task.value
        if !started, current == task { current = nil }
        return started
    }
}
