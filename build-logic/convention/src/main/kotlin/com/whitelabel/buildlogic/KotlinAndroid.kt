package com.whitelabel.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal const val COMPILE_SDK = 36
internal const val TARGET_SDK = 36
internal const val MIN_SDK = 26
internal const val JDK_VERSION = 17

/** Shared Android setup for library and application modules. */
internal fun Project.configureKotlinAndroid(extension: CommonExtension<*, *, *, *, *, *>) {
    extension.apply {
        compileSdk = COMPILE_SDK
        defaultConfig.minSdk = MIN_SDK
        compileOptions {
            sourceCompatibility = JavaVersion.toVersion(JDK_VERSION)
            targetCompatibility = JavaVersion.toVersion(JDK_VERSION)
        }
    }
    extensions.configure<KotlinAndroidProjectExtension> {
        jvmToolchain(JDK_VERSION)
    }
}

/**
 * Compose for library and application modules: the compiler plugin, the BOM, Material 3,
 * tooling and the instrumented UI-test dependencies.
 */
internal fun Project.configureCompose(extension: CommonExtension<*, *, *, *, *, *>) {
    pluginManager.apply(libs.pluginId("kotlin-compose"))
    extension.buildFeatures.compose = true
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
        // Espresso < 3.7 crashes on API 36 (InputManager.getInstance was removed).
        add("androidTestImplementation", libs.library("androidx-test-espresso-core"))
    }
}

internal fun Project.addTestDependencies(configuration: String = "testImplementation") {
    dependencies {
        add(configuration, libs.library("junit-jupiter"))
        add(configuration, libs.library("assertk"))
        add(configuration, libs.library("kotlinx-coroutines-test"))
        add(configuration.replace("Implementation", "RuntimeOnly"), libs.library("junit-platform-launcher"))
    }
    pointTestsAtTheContract()
}

/**
 * Contract tests (`:core:testing`'s `OpenApiSpec`) read the vendored `api/openapi.json`, two levels
 * above `core/testing`: the repo root here, `foundation/` in an app repo.
 */
internal fun Project.pointTestsAtTheContract() {
    val testing = rootProject.findProject(":core:testing") ?: return
    val spec = testing.projectDir.resolve("../../api/openapi.json").normalize()
    tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach {
        systemProperty("whitelabel.openapi", spec.absolutePath)
        inputs.file(spec).withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE).optional()
    }
}
