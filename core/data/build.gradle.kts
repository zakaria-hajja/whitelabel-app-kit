plugins {
    id("whitelabel.android.library")
    id("whitelabel.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.whitelabel.core.data"
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(libs.ktor.client.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.turbine)
}
