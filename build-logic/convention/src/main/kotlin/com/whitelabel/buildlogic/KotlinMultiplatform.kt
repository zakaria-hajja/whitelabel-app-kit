package com.whitelabel.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

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
            // Device tests are only for Android-specific code (Context, Keystore): their own source
            // set, not commonTest, which already runs on the JVM and iOS (and whose backtick names
            // D8 rejects below DEX 040).
            withDeviceTestBuilder {}.configure {
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
        // Device tests run under AndroidJUnitRunner, i.e. JUnit 4.
        add("androidDeviceTestImplementation", libs.library("kotlin-test-junit"))
        add("androidDeviceTestImplementation", libs.library("assertk"))
        add("androidDeviceTestImplementation", libs.library("kotlinx-coroutines-test"))
        add("androidDeviceTestImplementation", libs.library("androidx-test-runner"))
        add("androidDeviceTestImplementation", libs.library("androidx-test-ext-junit"))
    }

    tasks.withType<Test>().configureEach { useJUnitPlatform() }
    if (!iosTestsEnabled(mapOf(SKIP_IOS_TESTS_PROPERTY to providers.gradleProperty(SKIP_IOS_TESTS_PROPERTY).orNull))) {
        tasks.withType<KotlinNativeTest>().configureEach { enabled = false }
        tasks.withType<KotlinNativeLink>().configureEach { if (name.contains("Test")) enabled = false }
    }
    pointTestsAtTheContract()
}
