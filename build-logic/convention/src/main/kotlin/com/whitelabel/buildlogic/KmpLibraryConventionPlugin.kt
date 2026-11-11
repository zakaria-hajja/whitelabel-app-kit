package com.whitelabel.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * A Kotlin Multiplatform library shared by Android and iOS (see [configureKotlinMultiplatform]).
 * Platform code goes in `androidMain`/`iosMain`; everything else in `commonMain`.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-kotlin-multiplatform-library"))
        pluginManager.apply(libs.pluginId("kotlin-multiplatform"))
        pluginManager.apply(libs.pluginId("kotlin-serialization"))
        configureKotlinMultiplatform()
    }
}
