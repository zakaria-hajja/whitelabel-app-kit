import XCTest

/**
 * F-1: the Settings tab opens with its title visible, before any scroll, on both packs. Gallery
 * draws the bar on the page background (`flatPageChrome`).
 */
final class SettingsTitleUITests: XCTestCase {

    private let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
    }

    private func openSettings(_ extra: [String]) {
        app.launchFresh(extra)
        SignInRobot(app: app).signInToTabs()
        // A tap during the sign-in transition can be dropped: tap until the tab is selected.
        let tab = app.tabBars.buttons["Settings"]
        for _ in 0..<3 where !tab.isSelected {
            tab.tap()
            _ = XCTNSPredicateExpectation(predicate: NSPredicate(format: "isSelected == true"), object: tab).waitResult(2)
        }
        XCTAssertTrue(tab.isSelected, "couldn't open the Settings tab")
    }

    private func assertTitleVisibleAtRest(_ name: String) {
        // Let the tab switch settle so the bar shown is Settings'.
        XCTAssertTrue(app.buttons["Theme"].waitForExistence(timeout: 5), "Settings never showed")
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = "settings-\(name)"
        shot.lifetime = .keepAlways
        add(shot)
        let title = app.navigationBars.staticTexts["Settings"]
        XCTAssertTrue(title.waitForExistence(timeout: 5), "no Settings title in the bar")
        XCTAssertGreaterThan(title.frame.height, 10)
        // The element can exist (and be "hittable") while nothing is drawn: look for its glyphs.
        XCTAssertGreaterThan(Self.inkPixels(in: title.frame, of: app.screenshot().image), 50, "the Settings title isn't drawn at rest")
    }

    /** Pixels inside [frame] that clearly differ from the page colour sampled just left of it. */
    static func inkPixels(in frame: CGRect, of image: UIImage) -> Int {
        guard let cg = image.cgImage else { return 0 }
        let scale = CGFloat(cg.width) / image.size.width
        let width = cg.width, height = cg.height
        var data = [UInt8](repeating: 0, count: width * height * 4)
        guard let context = CGContext(data: &data, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4,
                                      space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return 0 }
        context.draw(cg, in: CGRect(x: 0, y: 0, width: width, height: height))
        func pixel(_ x: Int, _ y: Int) -> (Int, Int, Int) {
            let i = (y * width + x) * 4
            return (Int(data[i]), Int(data[i + 1]), Int(data[i + 2]))
        }
        let rect = CGRect(x: frame.minX * scale, y: frame.minY * scale, width: frame.width * scale, height: frame.height * scale).integral
        let page = pixel(max(Int(rect.minX) - 4, 0), Int(rect.midY))
        var ink = 0
        for y in max(Int(rect.minY), 0)..<min(Int(rect.maxY), height) {
            for x in max(Int(rect.minX), 0)..<min(Int(rect.maxX), width) {
                let p = pixel(x, y)
                if abs(p.0 - page.0) + abs(p.1 - page.1) + abs(p.2 - page.2) > 120 { ink += 1 }
            }
        }
        return ink
    }

    func testMaterialSettingsShowsItsTitleAtRest() {
        openSettings([])
        assertTitleVisibleAtRest("material")
    }

    func testGallerySettingsShowsItsTitleAtRest() {
        openSettings(["-galleryPack"])
        assertTitleVisibleAtRest("gallery")
    }
}

private extension XCTNSPredicateExpectation {
    func waitResult(_ seconds: TimeInterval) -> XCTWaiter.Result { XCTWaiter().wait(for: [self], timeout: seconds) }
}
