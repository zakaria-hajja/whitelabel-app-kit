package com.whitelabel.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-jvm"))
        extensions.configure<KotlinJvmProjectExtension> { jvmToolchain(JDK_VERSION) }
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
        addTestDependencies()
    }
}
