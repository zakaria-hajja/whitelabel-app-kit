plugins {
    id("whitelabel.android.library")
    id("whitelabel.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.whitelabel.core.mockapi"
}

// Debug and test builds only: the app plugin adds it as debugImplementation behind
// -Pwhitelabel.mockApi=true, and end-to-end tests add it as androidTestImplementation.
dependencies {
    implementation(project(":core:network"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.mock)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":core:testing"))
    testImplementation(project(":core:common"))
}
