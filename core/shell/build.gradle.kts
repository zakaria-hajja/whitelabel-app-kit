plugins {
    id("whitelabel.android.library")
    id("whitelabel.android.library.compose")
    id("whitelabel.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.whitelabel.core.shell"
}

dependencies {
    api(libs.androidx.navigation.compose)
    api(libs.compose.material.icons.core)
    implementation(libs.kotlinx.serialization.json)
}
