package com.whitelabel.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.BuildConfigField
import com.whitelabel.buildlogic.clients.ClientConfig
import com.whitelabel.buildlogic.clients.ClientConfigLoader
import com.whitelabel.buildlogic.apps.androidStringResource
import com.whitelabel.buildlogic.apps.environmentFor
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

private const val CLIENT_DIMENSION = "client"

/**
 * The `:app` module. Every directory under `clients/` becomes a product flavor in dimension
 * [CLIENT_DIMENSION]; per-variant identity and API values go into `BuildConfig`. No client is
 * named here: everything comes from [ClientConfigLoader].
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-application"))
        pluginManager.apply(libs.pluginId("kotlin-android"))
        pluginManager.apply(libs.pluginId("android-junit5"))
        // After the Android plugin, so whitelabel.hilt picks the Android variant.
        pluginManager.apply("whitelabel.hilt")

        val clients = ClientConfigLoader.load(rootDir.resolve("clients"))

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            configureCompose(this)
            defaultConfig {
                targetSdk = TARGET_SDK
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            buildFeatures.buildConfig = true
            lint.targetSdk = TARGET_SDK

            flavorDimensions += CLIENT_DIMENSION
            clients.forEach { client ->
                productFlavors.create(client.flavorName) {
                    dimension = CLIENT_DIMENSION
                    applicationId = client.applicationId
                    versionCode = client.versionCode
                    versionName = client.versionName
                    resValue("string", "app_name", androidStringResource(client.appName))
                    manifestPlaceholders["deepLinkScheme"] = client.deepLinkScheme
                }
                sourceSets.getByName(client.flavorName).res.srcDir(client.resDir)
                sourceSets.getByName("test${client.flavorName.replaceFirstChar(Char::uppercaseChar)}")
                    .kotlin.srcDir(client.testDir)
            }

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

        val clientsByFlavor = clients.associateBy { it.flavorName }
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants { variant ->
                val client = clientsByFlavor.getValue(requireNotNull(variant.flavorName))
                val buildType = requireNotNull(variant.buildType)
                // Non-null because buildFeatures.buildConfig is enabled above.
                val fields = requireNotNull(variant.buildConfigFields)
                brandFields(client, buildType).forEach { (name, value) ->
                    fields.put(name, BuildConfigField("String", value.asJavaString(), null))
                }
            }
        }
    }

    private fun brandFields(client: ClientConfig, buildType: String): Map<String, String> = mapOf(
        "CLIENT_SLUG" to client.slug,
        "APP_NAME" to client.appName,
        "API_BASE_URL" to client.apiBaseUrls.getValue(buildType),
        "ENVIRONMENT" to environmentFor(buildType),
        "DEEP_LINK_SCHEME" to client.deepLinkScheme,
        "SUPPORT_EMAIL" to client.supportEmail.orEmpty(),
        "PRIVACY_POLICY_URL" to client.privacyPolicyUrl,
        "TERMS_URL" to client.termsUrl,
        "DEFAULT_FEATURES" to client.defaultFeatures.joinToString(","),
    )

    private fun String.asJavaString(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
