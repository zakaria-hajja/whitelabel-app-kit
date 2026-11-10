package com.whitelabel.buildlogic

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-library"))
        pluginManager.apply(libs.pluginId("kotlin-android"))
        pluginManager.apply(libs.pluginId("android-junit5"))
        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            defaultConfig {
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            testOptions.targetSdk = TARGET_SDK
            lint.targetSdk = TARGET_SDK
        }
        // The shell's manifest declares the app's deep-link scheme as a placeholder that each app
        // fills in. A library's own test APK merges that manifest too, so it gets a value here; it
        // must not go in defaultConfig, which would bake it into the library manifest for every app.
        extensions.configure<LibraryAndroidComponentsExtension> {
            onVariants { variant ->
                variant.androidTest?.manifestPlaceholders?.put("deepLinkScheme", "foundation")
            }
        }
        addTestDependencies()
    }
}
