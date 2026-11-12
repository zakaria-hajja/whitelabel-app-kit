import XCTest
@testable import WhitelabelFirebase

/** Crashlytics' persisted flag and what was asked of it, in order. */
private final class RecordingCrash: CrashCollection {
    var enabled: Bool
    private(set) var calls: [String] = []

    init(enabled: Bool) { self.enabled = enabled }

    func isEnabled() -> Bool { enabled }
    func setEnabled(_ enabled: Bool) { calls.append("set(\(enabled))"); self.enabled = enabled }
    func deleteUnsentReports() { calls.append("delete") }
}

/** The same cases as Android's CrashRuleTest. */
final class CrashRuleTests: XCTestCase {

    func testFirstTurningOnDropsTheReportsCapturedWhileOff() {
        let crash = RecordingCrash(enabled: false)
        CrashRule(crash: crash).apply(enabled: true)
        XCTAssertEqual(crash.calls, ["delete", "set(true)"])
    }

    func testAlreadyOnFromAnEarlierLaunchKeepsItsReports() {
        let crash = RecordingCrash(enabled: true)
        CrashRule(crash: crash).apply(enabled: true)
        XCTAssertEqual(crash.calls, ["set(true)"])
    }

    func testTurningOffDeletesNothing() {
        let crash = RecordingCrash(enabled: true)
        CrashRule(crash: crash).apply(enabled: false)
        XCTAssertEqual(crash.calls, ["set(false)"])
    }

    func testTurningOnAgainAfterOffDropsWhatWasCapturedWhileOff() {
        let crash = RecordingCrash(enabled: true)
        let rule = CrashRule(crash: crash)
        rule.apply(enabled: false)
        rule.apply(enabled: true)
        XCTAssertEqual(crash.calls, ["set(false)", "delete", "set(true)"])
    }
}
