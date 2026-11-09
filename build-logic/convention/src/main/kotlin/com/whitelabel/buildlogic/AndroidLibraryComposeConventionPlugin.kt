package com.whitelabel.buildlogic

import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("whitelabel.android.library")
        pluginManager.apply(libs.pluginId("kotlin-compose"))
        extensions.configure<LibraryExtension> {
            buildFeatures.compose = true
        }
        dependencies {
            add("implementation", platform(libs.library("androidx-compose-bom")))
            add("androidTestImplementation", platform(libs.library("androidx-compose-bom")))
            add("implementation", libs.library("compose-ui"))
            add("implementation", libs.library("compose-ui-tooling-preview"))
            add("implementation", libs.library("compose-material3"))
            add("debugImplementation", libs.library("compose-ui-tooling"))
            add("debugImplementation", libs.library("compose-ui-test-manifest"))
            add("androidTestImplementation", libs.library("compose-ui-test-junit4"))
            add("androidTestImplementation", libs.library("androidx-test-ext-junit"))
        }
    }
}
