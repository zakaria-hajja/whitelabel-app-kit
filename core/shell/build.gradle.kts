plugins {
    id("whitelabel.android.library")
    id("whitelabel.android.library.compose")
    id("whitelabel.hilt")
    id("whitelabel.koin")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.whitelabel.core.shell"
}

dependencies {
    api(project(":core:branding"))
    api(project(":core:analytics"))
    api(project(":core:domain"))
    // Apps name a StylePack in their definition (style(StylePack.Gallery)).
    api(project(":core:designsystem"))
    api(libs.androidx.navigation.compose)
    api(libs.compose.material.icons.core)
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.assertk)
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:mockapi"))
}
