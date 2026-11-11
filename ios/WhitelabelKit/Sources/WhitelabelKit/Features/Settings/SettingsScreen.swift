import SwiftUI
import Shared

struct SettingsScreen: View {
    let options: SettingsOptions

    var body: some View {
        List {}
            .navigationTitle(KitStrings.text("Settings"))
    }
}
