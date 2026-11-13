plugins {
    id("whitelabel.android.library")
    id("whitelabel.android.library.compose")
    id("whitelabel.koin")
}

android {
    namespace = "com.whitelabel.core.ads"
}

// The ads API every ads app links: the banner, the DSL's builder and the port the SDK module fills.
// No Google SDK here; that is :core:ads:google, linked only where ads are on.
dependencies {
    api(project(":core:ads:shared"))
    // AdsServicesContributor extends ModuleContributor.
    api(project(":core:network"))
    implementation(libs.androidx.lifecycle.runtime.compose)
}
