package com.whitelabel.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.ResValue
import com.whitelabel.buildlogic.apps.AppConfigLoader
import com.whitelabel.buildlogic.apps.androidStringResource
import com.whitelabel.buildlogic.apps.appConfigFields
import com.whitelabel.buildlogic.apps.resourceNameFor
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * An app module under `apps/<name>/`. Identity and API settings come from the module's own
 * `app.properties` (validated by [AppConfigLoader]) and are published per variant twice, from one
 * map: as `BuildConfig` fields (for the app's identity test) and as `foundation_*` string
 * resources (read by `:core:shell`). No app is named here.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-application"))
        pluginManager.apply(libs.pluginId("kotlin-android"))
        pluginManager.apply(libs.pluginId("android-junit5"))
        // After the Android plugin, so whitelabel.hilt picks the Android variant.
        pluginManager.apply("whitelabel.hilt")

        val app = AppConfigLoader.load(projectDir, label = rootDir.toPath().relativize(projectDir.toPath()).toString())

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            configureCompose(this)
            defaultConfig {
                applicationId = app.applicationId
                versionCode = app.versionCode
                versionName = app.versionName
                targetSdk = TARGET_SDK
                testInstrumentationRunner = "com.whitelabel.core.testing.android.FoundationTestRunner"
                resValue("string", "app_name", androidStringResource(app.appName))
                manifestPlaceholders["deepLinkScheme"] = app.deepLinkScheme
            }
            app.signing?.let { signing ->
                signingConfigs.create("release") {
                    storeFile = signing.storeFile
                    storePassword = signing.storePassword
                    keyAlias = signing.keyAlias
                    keyPassword = signing.keyPassword
                }
            } ?: logger.info("${project.path}: no signing.properties, release will be unsigned")
            buildFeatures.buildConfig = true
            lint.targetSdk = TARGET_SDK
            // Android 13+ lists the app's languages in system settings; the list is generated from the
            // translated resources. AGP also needs the default resources' language (English), and
            // reads it only from a source res dir, not a generated one: so it is a checked-in file,
            // which clean and the configuration cache can't lose.
            androidResources.generateLocaleConfig = true
            // It sits next to the shell, which every app includes, so app repos find it too.
            sourceSets.getByName("main").res.srcDir(project(":core:shell").projectDir.resolve("default-locale-res"))

            buildTypes {
                getByName("debug") {
                    applicationIdSuffix = ".debug"
                }
                getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    signingConfig = signingConfigs.findByName("release")
                }
            }
        }

        addTestDependencies()

        // End-to-end tests: a Hilt test app talking to the in-memory backend, never a server.
        dependencies.add("androidTestImplementation", project(":core:testing-android"))
        dependencies.add("androidTestImplementation", project(":core:mockapi"))
        dependencies.add("androidTestImplementation", libs.library("hilt-android-testing"))
        dependencies.add("kspAndroidTest", libs.library("hilt-compiler"))

        // -Pwhitelabel.mockApi=true: debug builds talk to :core:mockapi instead of a server.
        mockApiDependency(mapOf(MOCK_API_PROPERTY to providers.gradleProperty(MOCK_API_PROPERTY).orNull))
            ?.let { (configuration, module) -> dependencies.add(configuration, project(module)) }

        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants { variant ->
                val buildType = requireNotNull(variant.buildType)
                // Non-null because buildFeatures.buildConfig is enabled above.
                val buildConfig = requireNotNull(variant.buildConfigFields)
                appConfigFields(app, buildType).forEach { (name, value) ->
                    buildConfig.put(name, BuildConfigField("String", value.asJavaString(), null))
                    variant.resValues.put(
                        variant.makeResValueKey("string", resourceNameFor(name)),
                        ResValue(androidStringResource(value), null),
                    )
                }
            }
        }
    }

    private fun String.asJavaString(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
