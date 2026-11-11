plugins {
    id("whitelabel.android.library")
    id("whitelabel.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.whitelabel.core.reminders"
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.turbine)
}
