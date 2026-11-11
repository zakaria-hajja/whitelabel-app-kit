import XCTest
@testable import WhitelabelKit

final class IosLanguageRepositoryTests: XCTestCase {

    private var defaults: UserDefaults!

    override func setUp() {
        defaults = UserDefaults(suiteName: "wl.language-tests")
        defaults.removePersistentDomain(forName: "wl.language-tests")
    }

    func testStoresTheChoice() {
        let repository = IosLanguageRepository(defaults: defaults)
        repository.set(languageTag: "fr")
        XCTAssertEqual(repository.current(), "fr")
        XCTAssertEqual(defaults.stringArray(forKey: "AppleLanguages"), ["fr"])
    }

    func testNilFollowsTheDevice() {
        let repository = IosLanguageRepository(defaults: defaults)
        repository.set(languageTag: "fr")
        repository.set(languageTag: nil)
        XCTAssertNil(repository.current())
        // Only the app's own override goes; reads still fall back to the device's languages.
        XCTAssertNil(defaults.persistentDomain(forName: "wl.language-tests")?["AppleLanguages"])
    }

    /** iOS reads the app's language only at launch, so settings tells the user it applies next time. */
    func testAChangeAppliesAtTheNextLaunch() {
        XCTAssertFalse(IosLanguageRepository(defaults: defaults).appliesImmediately)
    }
}
