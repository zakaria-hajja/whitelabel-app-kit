package com.whitelabel.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Koin for a module's dependency injection: `koin-core`, plus `koin-android` on Android modules and
 * `koin-androidx-compose` (for `koinViewModel()`) on Compose ones; KMP modules get `koin-core` in
 * `commonMain`. Tests get `koin-test` (its `verify()` checks a module's graph on the JVM).
 */
class KoinConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            dependencies {
                add("commonMainImplementation", libs.library("koin-core"))
                add("jvmTestImplementation", libs.library("koin-test"))
            }
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            dependencies {
                add("implementation", libs.library("koin-core"))
                add("testImplementation", libs.library("koin-test"))
            }
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.android") {
            dependencies {
                add("implementation", libs.library("koin-core"))
                add("implementation", libs.library("koin-android"))
                add("testImplementation", libs.library("koin-test"))
            }
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
            if (!pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                dependencies { add("implementation", libs.library("koin-androidx-compose")) }
            }
        }
    }
}
