plugins {
    id("whitelabel.android.library")
    id("whitelabel.android.library.compose")
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
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.turbine)
    androidTestImplementation(libs.assertk)
    androidTestImplementation(libs.androidx.test.runner)
}
