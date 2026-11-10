plugins {
    id("whitelabel.android.application")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.whitelabel.app"
}

dependencies {
    implementation(project(":core:branding"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:designsystem"))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.turbine)
}
