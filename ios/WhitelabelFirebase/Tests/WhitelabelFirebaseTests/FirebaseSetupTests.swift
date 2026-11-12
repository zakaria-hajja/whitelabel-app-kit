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
