package com.whitelabel.buildlogic

import com.whitelabel.buildlogic.apps.AppConfigLoader
import com.whitelabel.buildlogic.apps.iosFirebaseFramework
import com.whitelabel.buildlogic.apps.iosXcconfig
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

/** Where an app's Android module (with its `app.properties`) and its iOS project's `Config/` dir are. */
abstract class IosAppExtension {
    abstract val androidApp: DirectoryProperty
    abstract val iosConfigDir: DirectoryProperty

    /** The app's `GoogleService-Info.plist`; defaults to `<iosConfigDir>/../<project dir>/GoogleService-Info.plist`. */
    abstract val firebaseConfigFile: RegularFileProperty
}

/** Writes `Debug.generated.xcconfig` and `Release.generated.xcconfig` from the app's `app.properties`. */
abstract class GenerateIosConfig : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val androidApp: DirectoryProperty

    /** The plist may be absent; only whether it exists changes the output, so only that is an input. */
    @get:Internal
    abstract val firebaseConfigFile: RegularFileProperty

    @get:Input
    abstract val firebaseConfigFileExists: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val app = AppConfigLoader.load(androidApp.get().asFile)
        val exists = firebaseConfigFileExists.get()
        val out = outputDir.get().asFile
        out.resolve("Debug.generated.xcconfig").writeText(iosXcconfig(app, "debug", exists))
        out.resolve("Release.generated.xcconfig").writeText(iosXcconfig(app, "release", exists))
    }
}

/**
 * For an app's iOS framework module (`:apps:<name>:shared`): `generateIosConfig` from the app's
 * identity, and, when the app has Firebase (`firebase.enabled` true or debug), CrashKiOS' link plugin
 * and the Kotlin crash hook module, added and exported ([iosFirebaseFramework]). An app without
 * Firebase gets neither.
 */
class IosAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val extension = extensions.create<IosAppExtension>("iosApp")
        tasks.register<GenerateIosConfig>("generateIosConfig") {
            group = "ios"
            description = "Writes the iOS project's xcconfig files from the app's app.properties"
            androidApp.set(extension.androidApp)
            outputDir.set(extension.iosConfigDir)
            firebaseConfigFile.set(
                extension.firebaseConfigFile.orElse(
                    extension.iosConfigDir.map { it.file("../${it.asFile.parentFile.name}/GoogleService-Info.plist") },
                ),
            )
            // providers.fileContents makes the file a tracked configuration-cache input, unlike File.isFile.
            firebaseConfigFileExists.set(
                providers.fileContents(firebaseConfigFile).asBytes.map { true }.orElse(false),
            )
        }
        // The app's directory is set in the module's build script, so its properties are read after it.
        afterEvaluate {
            val app = AppConfigLoader.load(extension.androidApp.get().asFile)
            val firebase = iosFirebaseFramework(app.firebase)
            if (firebase.crashLinkPlugin) pluginManager.apply(libs.pluginId("crashkios-crashlyticslink"))
            if (firebase.modules.isNotEmpty()) {
                val kotlin = extensions.getByType<KotlinMultiplatformExtension>()
                kotlin.sourceSets.getByName("commonMain").dependencies {
                    firebase.modules.forEach { api(project(it)) }
                }
                kotlin.targets.withType<KotlinNativeTarget>().configureEach {
                    binaries.withType<Framework>().configureEach { firebase.modules.forEach { export(project(it)) } }
                }
            }
        }
        Unit
    }
}
