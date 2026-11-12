plugins {
    id("whitelabel.android.library")
    id("whitelabel.koin")
}

android {
    namespace = "com.whitelabel.core.firebase.google"
}

// Apps link this module only into build types where Firebase is on (see the firebase setting).
dependencies {
    api(project(":core:firebase:shared"))
    api(project(":core:consent:google"))
    implementation(project(":core:network"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
}
