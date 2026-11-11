package com.whitelabel.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Strings that shared code shows (through `UiText`), as Compose Multiplatform resources in
 * `src/commonMain/composeResources`: one translation source for Android and iOS. The generated
 * `Res` class is public, in `<namespace>.resources`. Apply after `whitelabel.kmp.library`.
 */
class KmpResourcesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("compose-multiplatform"))
        pluginManager.apply(libs.pluginId("kotlin-compose"))
        extensions.configure<KotlinMultiplatformExtension> {
            (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
            }
        }
        extensions.configure<ComposeExtension> {
            (this as ExtensionAware).extensions.configure<ResourcesExtension> {
                publicResClass = true
                packageOfResClass = "${kmpNamespace(path)}.resources"
            }
        }
        val translations = tasks.register<CheckComposeTranslations>("checkComposeTranslations") {
            resourcesDir.set(layout.projectDirectory.dir("src/commonMain/composeResources"))
        }
        tasks.named("check") { dependsOn(translations) }
        dependencies {
            add("commonMainImplementation", libs.library("jetbrains-compose-runtime"))
            add("commonMainApi", libs.library("jetbrains-compose-components-resources"))
        }
    }
}
