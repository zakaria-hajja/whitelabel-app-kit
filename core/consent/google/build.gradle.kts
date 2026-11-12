plugins {
    id("whitelabel.android.library")
    id("whitelabel.koin")
}

android {
    namespace = "com.whitelabel.core.consent.google"
}

// Apps link this module only into build types where Firebase and ads are enabled.
dependencies {
    api(project(":core:consent:shared"))
    implementation(project(":core:network"))
    implementation(libs.google.ump)
}
