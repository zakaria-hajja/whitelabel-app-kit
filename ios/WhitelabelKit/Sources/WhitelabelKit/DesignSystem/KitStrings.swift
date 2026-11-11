import SwiftUI

/**
 * The kit's own screen labels, from `ios/WhitelabelKit/Resources/WhitelabelKit.xcstrings` (en, fr).
 * The kit is a static framework, so its resources don't ship by themselves: each app's project adds
 * that file to the app target, and the kit reads it from the main bundle as table "WhitelabelKit".
 * Messages from ViewModels never come from here: they are shared strings (`UiTextView`).
 */
enum KitStrings {
    static let table = "WhitelabelKit"

    static func text(_ key: String.LocalizationValue) -> String {
        String(localized: key, table: table)
    }
}
