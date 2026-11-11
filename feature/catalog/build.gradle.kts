plugins {
    id("whitelabel.android.library")
    id("whitelabel.android.library.compose")
    id("whitelabel.koin")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.whitelabel.feature.catalog"
}

dependencies {
    api(project(":feature:catalog:shared"))
    implementation(project(":core:presentation"))
    implementation(project(":core:shell"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:analytics"))
    implementation(libs.ktor.client.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.assertk)
    testImplementation(project(":core:testing"))
}
