package com.whitelabel.buildlogic

import com.whitelabel.buildlogic.apps.AppConfigLoader
import com.whitelabel.buildlogic.apps.iosXcconfig
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

/** Where an app's Android module (with its `app.properties`) and its iOS project's `Config/` dir are. */
abstract class IosAppExtension {
    abstract val androidApp: DirectoryProperty
    abstract val iosConfigDir: DirectoryProperty
}

/** Writes `Debug.generated.xcconfig` and `Release.generated.xcconfig` from the app's `app.properties`. */
abstract class GenerateIosConfig : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val androidApp: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val app = AppConfigLoader.load(androidApp.get().asFile)
        val out = outputDir.get().asFile
        out.resolve("Debug.generated.xcconfig").writeText(iosXcconfig(app, "debug"))
        out.resolve("Release.generated.xcconfig").writeText(iosXcconfig(app, "release"))
    }
}

/** For an app's iOS framework module (`:apps:<name>:shared`): `generateIosConfig` from the app's identity. */
class IosAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val extension = extensions.create<IosAppExtension>("iosApp")
        tasks.register<GenerateIosConfig>("generateIosConfig") {
            group = "ios"
            description = "Writes the iOS project's xcconfig files from the app's app.properties"
            androidApp.set(extension.androidApp)
            outputDir.set(extension.iosConfigDir)
        }
        Unit
    }
}
