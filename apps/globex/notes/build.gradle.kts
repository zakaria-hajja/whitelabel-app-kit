plugins {
    id("whitelabel.android.library")
    id("whitelabel.android.library.compose")
    id("whitelabel.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.whitelabel.apps.globex.notes"
}

dependencies {
    implementation(project(":core:shell"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.turbine)
    androidTestImplementation(libs.assertk)
}
