plugins {
    id("whitelabel.android.library")
    id("whitelabel.android.library.compose")
    id("whitelabel.koin")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.whitelabel.core.shell"
}

dependencies {
    api(project(":core:branding"))
    api(project(":core:shell:shared"))
    api(project(":core:analytics"))
    api(project(":core:domain"))
    // Apps name a StylePack in their definition (style(StylePack.Gallery)).
    api(project(":core:designsystem"))
    api(libs.androidx.navigation.compose)
    api(libs.compose.material.icons.core)
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:firebase:shared"))
    implementation(project(":core:consent:shared"))
    // The banner and the ads rules; the Google SDK (:core:ads:google) is linked only by apps with ads on.
    implementation(project(":core:ads"))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // The content gate hears the app's foreground/background from ProcessLifecycleOwner.
    implementation(libs.androidx.lifecycle.process)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.turbine)
    testImplementation(libs.assertk)
    androidTestImplementation(libs.assertk)
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:mockapi"))
    // AdsWiringTest points the ads stores at a temporary directory.
    testImplementation(project(":core:datastore"))
}
