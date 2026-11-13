plugins {
    id("whitelabel.android.library")
    id("whitelabel.android.library.compose")
    id("whitelabel.koin")
}

android {
    namespace = "com.whitelabel.core.ads.google"
}

// Apps link this module only into build types where ads are on (see the ads setting).
dependencies {
    api(project(":core:ads"))
    api(project(":core:consent:google"))
    implementation(project(":core:network"))
    implementation(libs.play.services.ads)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
