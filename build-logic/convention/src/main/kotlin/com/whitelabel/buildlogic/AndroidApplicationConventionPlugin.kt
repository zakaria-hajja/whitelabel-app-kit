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

        val app = AppConfigLoader.load(projectDir)

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            configureCompose(this)
            defaultConfig {
                applicationId = app.applicationId
                versionCode = app.versionCode
                versionName = app.versionName
                targetSdk = TARGET_SDK
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                resValue("string", "app_name", androidStringResource(app.appName))
                manifestPlaceholders["deepLinkScheme"] = app.deepLinkScheme
            }
            buildFeatures.buildConfig = true
            lint.targetSdk = TARGET_SDK

            buildTypes {
                getByName("debug") {
                    applicationIdSuffix = ".debug"
                }
                getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                }
            }
        }

        addTestDependencies()

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
