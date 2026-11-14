import XCTest
import Shared
@testable import WhitelabelFirebase

/** The adapter's forwarding; consent gating around it is tested in Kotlin (`ConsentGatedAnalyticsTest`). */
final class FirebaseAnalyticsAdapterTests: XCTestCase {

    func testAUserPropertyGoesToFirebaseUnderItsName() {
        var sent: [(String, String)] = []
        let adapter = FirebaseAnalyticsAdapter(setUserProperty: { value, name in sent.append((name, value)) })
        adapter.setUserProperty(property: UserProperty(name: "garden_size_bucket", value: "3_5"))
        XCTAssertEqual(sent.map { "\($0.0)=\($0.1)" }, ["garden_size_bucket=3_5"])
    }
}
