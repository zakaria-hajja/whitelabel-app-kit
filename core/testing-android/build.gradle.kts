plugins {
    id("whitelabel.android.library")
    id("whitelabel.hilt")
}

android {
    namespace = "com.whitelabel.core.testing.android"
}

// Instrumented-test support for app modules: added as androidTestImplementation by the app plugin.
dependencies {
    implementation(project(":core:data"))
    api(platform(libs.androidx.compose.bom))
    api(libs.compose.ui.test.junit4)
    api(libs.androidx.test.runner)
    api(libs.hilt.android.testing)
    // FoundationActivity is an AppCompatActivity; tests that launch it need the supertype.
    api(libs.androidx.appcompat)
}
