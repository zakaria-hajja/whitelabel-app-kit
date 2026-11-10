package com.whitelabel.buildlogic

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
        addTestDependencies()
    }
}
