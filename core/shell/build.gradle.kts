plugins {
    id("whitelabel.android.library")
    id("whitelabel.android.library.compose")
    id("whitelabel.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.whitelabel.core.shell"
}

// The manifest's deepLinkScheme placeholder is filled in by each app (application plugin). Only this
// module's own test APK gets a value here: a library defaultConfig value would be baked into the
// library manifest and override every app's scheme.
androidComponents {
    onVariants { variant ->
        variant.androidTest?.manifestPlaceholders?.put("deepLinkScheme", "foundation")
    }
}

dependencies {
    api(project(":core:branding"))
    api(project(":core:analytics"))
    api(project(":core:domain"))
    api(libs.androidx.navigation.compose)
    api(libs.compose.material.icons.core)
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
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
    androidTestImplementation(libs.assertk)
}
