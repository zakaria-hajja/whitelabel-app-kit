package com.whitelabel.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("ksp"))
        val isAndroid = pluginManager.hasPlugin("com.android.base")
        if (isAndroid) pluginManager.apply(libs.pluginId("hilt"))
        dependencies {
            add("implementation", libs.library(if (isAndroid) "hilt-android" else "hilt-core"))
            add("ksp", libs.library("hilt-compiler"))
        }
    }
}
