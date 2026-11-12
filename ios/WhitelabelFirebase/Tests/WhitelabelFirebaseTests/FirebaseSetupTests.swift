import XCTest
import Shared
import UserMessagingPlatform
@testable import WhitelabelConsent
@testable import WhitelabelFirebase

/** The plist check, the fetch intervals and the UMP status mapping (the SDKs themselves are checked on a device). */
final class FirebaseSetupTests: XCTestCase {

    private let collectionOff: [String: Any] = Dictionary(uniqueKeysWithValues: FirebaseService.collectionOffKeys.map { ($0, false) })

    func testAPlistWithCollectionOffPasses() {
        XCTAssertNil(FirebaseService.collectionProblem(collectionOff))
    }

    func testAMissingOrTrueKeyIsNamed() {
        var info = collectionOff
        info["FirebaseCrashlyticsCollectionEnabled"] = nil
        info["GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_STORAGE"] = true
        let problem = FirebaseService.collectionProblem(info)
        XCTAssertEqual(
            problem,
            "Firebase is on, but Info.plist doesn't start collection off until consent; set these to NO (Boolean): FirebaseCrashlyticsCollectionEnabled, GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_STORAGE"
        )
    }

    func testTheAdMobAppIdMustBePresent() {
        XCTAssertNil(FirebaseService.adMobProblem(["GADApplicationIdentifier": "ca-app-pub-1234567890123456~1234567890"]))
        for info in [[:], ["GADApplicationIdentifier": ""], ["GADApplicationIdentifier": "   "], ["GADApplicationIdentifier": 7]] as [[String: Any]] {
            let problem = FirebaseService.adMobProblem(info)
            XCTAssertNotNil(problem)
            XCTAssertTrue(problem!.contains("GADApplicationIdentifier"))
        }
    }

    func testDebugBuildsFetchAtOnceAndReleaseBuildsEveryTwelveHours() {
        XCTAssertEqual(FetchInterval.seconds(debugBuild: true), 0)
        XCTAssertEqual(FetchInterval.seconds(debugBuild: false), 43_200)
    }

    func testUmpStatusesMapToTheSharedOnes() {
        XCTAssertEqual(umpStatus(of: .required), .required)
        XCTAssertEqual(umpStatus(of: .notRequired), .notRequired)
        XCTAssertEqual(umpStatus(of: .obtained), .obtained)
        XCTAssertEqual(umpStatus(of: .unknown), .unknown)
    }

    @MainActor func testOffAndFakeModesConfigureNothing() {
        // FirebaseApp.configure() without GoogleService-Info.plist would stop the test process.
        FirebaseService().install(mode: "off")
        FirebaseService().install(mode: "fake")
    }
}

/** `-analyticsDebug` turns on Firebase's DebugView, as the `-FIRDebugEnabled` argument does. */
final class DebugViewTests: XCTestCase {

    private let suite = "wl.firebase.debugview.tests"

    override func tearDown() { UserDefaults(suiteName: suite)?.removePersistentDomain(forName: suite) }

    func testAnalyticsDebugTurnsOnFirebasesDebugMode() {
        let defaults = UserDefaults(suiteName: suite)!
        FirebaseService.applyDebugView(true, to: defaults)
        XCTAssertTrue(defaults.bool(forKey: "/google/firebase/debug_mode"))
        XCTAssertTrue(defaults.bool(forKey: "/google/measurement/debug_mode"))
    }

    func testWithoutTheArgumentAnEarlierRunsDebugModeIsCleared() {
        let defaults = UserDefaults(suiteName: suite)!
        FirebaseService.applyDebugView(true, to: defaults)
        FirebaseService.applyDebugView(false, to: defaults)
        XCTAssertNil(defaults.object(forKey: "/google/firebase/debug_mode"))
        XCTAssertNil(defaults.object(forKey: "/google/measurement/debug_mode"))
    }
}
