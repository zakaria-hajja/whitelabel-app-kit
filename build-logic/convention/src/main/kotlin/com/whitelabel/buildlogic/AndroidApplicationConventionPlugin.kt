package com.whitelabel.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.ResValue
import com.whitelabel.buildlogic.apps.AppConfigLoader
import com.whitelabel.buildlogic.apps.androidStringResource
import com.whitelabel.buildlogic.apps.ServiceMode
import com.whitelabel.buildlogic.apps.appConfigFields
import com.whitelabel.buildlogic.apps.Platform
import com.whitelabel.buildlogic.apps.adsSetup
import com.whitelabel.buildlogic.apps.adsUnitsString
import com.whitelabel.buildlogic.apps.resolvedAdmobAppId
import com.whitelabel.buildlogic.apps.firebaseSetup
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
        // After the Kotlin Android plugin, so whitelabel.koin adds the Android artifacts.
        pluginManager.apply("whitelabel.koin")

        val app = AppConfigLoader.load(projectDir, label = rootDir.toPath().relativize(projectDir.toPath()).toString())

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

        // End-to-end tests: the real app (a fresh Koin graph per test) talking to the in-memory
        // backend, never a server.
        dependencies.add("androidTestImplementation", project(":core:testing-android"))
        dependencies.add("androidTestImplementation", project(":core:mockapi"))

        // -Pwhitelabel.mockApi=true: debug builds talk to :core:mockapi instead of a server.
        mockApiDependency(mapOf(MOCK_API_PROPERTY to providers.gradleProperty(MOCK_API_PROPERTY).orNull))
            ?.let { (configuration, module) -> dependencies.add(configuration, project(module)) }

        // Firebase: libraries per build type, the google-services plugins when the config file is
        // there, and the mode the shell reads. Release never gets the libraries in `debug` mode.
        val firebase = firebaseSetup(
            app.firebase,
            configFileExists = projectDir.resolve("google-services.json").isFile,
            adsAppId = app.adsAndroidAppId,
            label = rootDir.toPath().relativize(projectDir.toPath()).toString(),
        )
        if (firebase.applyGradlePlugins) {
            pluginManager.apply(libs.pluginId("google-services"))
            pluginManager.apply(libs.pluginId("firebase-crashlytics"))
        }
        if (app.firebase != ServiceMode.OFF) {
            dependencies.add("implementation", project(":core:firebase:shared"))
        }
        // Ads: the Google module per build type, and the shared ads modules whenever ads aren't off.
        // An app with ads off gets none of this. The AdMob app ID is one manifest placeholder that
        // :core:consent:google's manifest and the ads SDK both read: the ads value when ads are on
        // for the build type (the real ID, or Google's test ID in Debug without one), else the
        // plain ads.android.appId Firebase's consent needs, else empty so merging never fails.
        val ads = adsSetup(
            app.ads, app.adsAndroidAppId, app.adsAndroidUnits, Platform.ANDROID,
            label = rootDir.toPath().relativize(projectDir.toPath()).toString(),
        )
        if (app.ads != ServiceMode.OFF) {
            dependencies.add("implementation", project(":core:ads"))
            dependencies.add("implementation", project(":core:ads:shared"))
        }
        ads.dependencyConfiguration?.let { dependencies.add(it, project(":core:ads:google")) }
        extensions.configure<ApplicationExtension> {
            defaultConfig.manifestPlaceholders["admobAppId"] = resolvedAdmobAppId(ads, app.adsAndroidAppId, "release")
            buildTypes {
                getByName("debug").manifestPlaceholders["admobAppId"] = resolvedAdmobAppId(ads, app.adsAndroidAppId, "debug")
                getByName("release").manifestPlaceholders["admobAppId"] = resolvedAdmobAppId(ads, app.adsAndroidAppId, "release")
            }
        }
        firebase.dependencyConfiguration?.let { dependencies.add(it, project(":core:firebase:google")) }
        if (firebase.disabledTasks.isNotEmpty()) {
            tasks.configureEach { if (name in firebase.disabledTasks) enabled = false }
        }

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
                // Resources only, not BuildConfig: they are build-wiring state for the shell, not
                // app identity, so the apps' identity tests stay as they are.
                mapOf(
                    "foundation_firebase" to firebase.modeFor.getValue(buildType),
                    "foundation_firebase_declared" to (app.firebase != ServiceMode.OFF).toString(),
                    "foundation_ads" to ads.modeFor.getValue(buildType),
                    "foundation_ads_declared" to ads.declared.toString(),
                    "foundation_ads_units" to adsUnitsString(ads.unitsFor.getValue(buildType)),
                ).forEach { (name, value) ->
                    variant.resValues.put(variant.makeResValueKey("string", name), ResValue(value, null))
                }
            }
        }
    }

    private fun String.asJavaString(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
