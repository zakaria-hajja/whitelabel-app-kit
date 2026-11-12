import XCTest

/** The catalog end to end on the fake backend, mirroring Android's showcase E2E. */
final class CatalogUITests: XCTestCase {

    private let app = XCUIApplication()
    private var signIn: SignInRobot { SignInRobot(app: app) }
    private var catalog: CatalogRobot { CatalogRobot(app: app) }

    override func setUp() { continueAfterFailure = false }

    func testSearchFilterSortAndOpenAnItem() {
        app.launchFresh()
        signIn.signInToTabs()
        catalog.open()
        catalog.search("Item 1")
        catalog.showsInOrder("Item 19", "Item 18", "Item 17") // newest first
        catalog.doesNotSee("Item 25") // the top of the unfiltered list
        catalog.filter("Music")
        catalog.showsInOrder("Item 19", "Item 15", "Item 11")
        catalog.doesNotSee("Item 18")
        catalog.doesNotSee("Item 23") // Music, but doesn't match "Item 1"
        catalog.sortBy("Title A–Z")
        catalog.showsInOrder("Item 11", "Item 15", "Item 19")
        catalog.open("Item 11")
        catalog.showsDetail("Sample item 11")
    }

    func testSwitchedOffHidesTabAndShortcut() {
        app.launchFresh(["-fakeFeatures", "catalog=false"])
        signIn.signInToTabs()
        XCTAssertTrue(HomeRobot(app: app).greeting("Hello, Alice").waitForExistence(timeout: 10))
        XCTAssertFalse(app.tabBars.buttons["Catalog"].exists)
        XCTAssertFalse(HomeRobot(app: app).shortcut("catalog").exists)
    }

    /** Review Focus 2: a catalog link while signed out waits for sign-in, then opens. */
    func testCatalogLinkWhileSignedOutOpensAfterSignIn() {
        app.launchFresh()
        XCTAssertTrue(signIn.waitForSignIn())
        app.openLink("showcase://catalog/01950000-0000-7000-8000-000000000307") // seeded Item 7
        XCTAssertTrue(signIn.title.exists) // held, not opened over sign-in
        signIn.signIn()
        signIn.dismissSavePassword()
        catalog.showsDetail("Sample item 7")
    }
}
