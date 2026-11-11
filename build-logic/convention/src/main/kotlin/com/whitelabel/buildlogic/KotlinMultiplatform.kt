package com.whitelabel.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * The Android namespace (and package) of a KMP module, from its Gradle path: `:core:common` →
 * `com.whitelabel.core.common`. Dashes are dropped because a package name can't contain them.
 */
internal fun kmpNamespace(projectPath: String): String =
    "com.whitelabel" + projectPath.lowercase().replace("-", "").replace(':', '.')

/**
 * Targets and test setup shared by every KMP library: Android (the new KMP library plugin), iOS
 * device and simulator, and a JVM target that ships nowhere. The JVM target runs the host tests on
 * plain JUnit 5 and lets JVM-only test fixtures (`:core:testing`) be used from `jvmTest`.
 */
internal fun Project.configureKotlinMultiplatform() {
    extensions.configure<KotlinMultiplatformExtension> {
        (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
            namespace = kmpNamespace(path)
            compileSdk = COMPILE_SDK
            minSdk = MIN_SDK
            withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
        }
        iosArm64()
        iosSimulatorArm64()
        jvm()
        jvmToolchain(JDK_VERSION)
        applyDefaultHierarchyTemplate()
    }

    dependencies {
        add("commonMainImplementation", libs.library("kotlinx-coroutines-core"))
        add("commonMainImplementation", libs.library("kotlinx-serialization-json"))
        add("commonTestImplementation", libs.library("kotlin-test"))
        add("commonTestImplementation", libs.library("assertk"))
        add("commonTestImplementation", libs.library("turbine"))
        add("commonTestImplementation", libs.library("kotlinx-coroutines-test"))
        add("jvmTestImplementation", libs.library("kotlin-test-junit5"))
        add("jvmTestImplementation", libs.library("junit-jupiter"))
        add("jvmTestRuntimeOnly", libs.library("junit-platform-launcher"))
    }

    tasks.withType<Test>().configureEach { useJUnitPlatform() }
    pointTestsAtTheContract()
}
