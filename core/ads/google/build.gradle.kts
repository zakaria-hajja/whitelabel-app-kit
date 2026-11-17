plugins {
    id("whitelabel.android.library")
    id("whitelabel.android.library.compose")
    id("whitelabel.koin")
}

android {
    namespace = "com.whitelabel.core.ads.google"
}

// The device tests' own APK merges the consent manifest's AdMob app ID; the app plugin fills it for
// apps, so the tests use Google's public test app ID.
androidComponents {
    onVariants { variant ->
        variant.androidTest?.manifestPlaceholders?.put("admobAppId", "ca-app-pub-3940256099942544~3347511713")
    }
}

// Apps link this module only into build types where ads are on (see the ads setting).
dependencies {
    api(project(":core:ads"))
    api(project(":core:consent:google"))
    implementation(project(":core:network"))
    // The native template's spacing (AppTheme.spacing).
    implementation(project(":core:designsystem"))
    implementation(libs.play.services.ads)
    implementation(libs.androidx.lifecycle.runtime.compose)

    androidTestImplementation(libs.assertk)
    androidTestImplementation(libs.androidx.test.runner)
}
