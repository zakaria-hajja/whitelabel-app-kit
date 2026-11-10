plugins {
    id("whitelabel.android.library")
    id("whitelabel.android.library.compose")
}

android {
    namespace = "com.whitelabel.core.designsystem"
}

dependencies {
    implementation(libs.compose.material.icons.core)

    androidTestImplementation(libs.assertk)
}
