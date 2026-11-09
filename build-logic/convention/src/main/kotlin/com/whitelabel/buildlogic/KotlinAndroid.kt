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

internal fun Project.addTestDependencies(configuration: String = "testImplementation") {
    dependencies {
        add(configuration, libs.library("junit-jupiter"))
        add(configuration, libs.library("assertk"))
        add(configuration, libs.library("kotlinx-coroutines-test"))
        add(configuration.replace("Implementation", "RuntimeOnly"), libs.library("junit-platform-launcher"))
    }
}
