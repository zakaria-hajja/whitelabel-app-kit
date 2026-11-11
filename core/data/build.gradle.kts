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
    implementation(project(":core:branding"))
    implementation(libs.ktor.client.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.assertk)
    androidTestImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    testImplementation(project(":core:testing"))
}
