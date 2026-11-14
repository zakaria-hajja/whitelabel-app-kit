package com.whitelabel.buildlogic.apps

import java.io.File

/**
 * What one app gets for Firebase. [dependencyConfiguration] is the Gradle configuration for the
 * Firebase libraries (`null` for none), [applyGradlePlugins] whether the google-services plugins
 * apply, and [modeFor] the mode per build type (`debug`/`release`) written as the
 * `foundation_firebase` string on Android and `WL_FIREBASE` on iOS: `on`, `fake` or `off`.
 * [disabledTasks] are Gradle tasks (by name) to switch off: in `debug` mode the plugins also see the
 * release variant, which must not process the config file or upload a mapping.
 */
data class FirebaseSetup(
    val dependencyConfiguration: String?,
    val applyGradlePlugins: Boolean,
    val modeFor: Map<String, String>,
    val disabledTasks: List<String> = emptyList(),
)

/** The build types an app's Firebase config file can cover. */
val FIREBASE_BUILD_TYPES: Set<String> = setOf("debug", "release")

/**
 * Decides the Firebase setup from `firebase.enabled`, the build types that have the platform's
 * config file ([configFileBuildTypes]) and the platform's AdMob app ID [adsAppId] (Google's consent
 * SDK needs it). [configFileName], [adsAppIdKey] and [label] only shape the errors for `true`
 * without the files or the ID: with no file at all the error names the file, with some build types
 * missing it also names those. In `debug` mode, debug builds are real only with a debug config file
 * and the ID; otherwise they use the fake so the app still runs. `false` ignores both.
 */
fun firebaseSetup(
    mode: ServiceMode,
    configFileBuildTypes: Set<String>,
    adsAppId: String? = null,
    label: String = "app",
    configFileName: String = "google-services.json",
    adsAppIdKey: String = "ads.android.appId",
): FirebaseSetup {
    val hasAppId = !adsAppId.isNullOrBlank()
    val missing = FIREBASE_BUILD_TYPES - configFileBuildTypes
    return when (mode) {
        ServiceMode.OFF -> FirebaseSetup(null, false, mapOf("debug" to "off", "release" to "off"))
        ServiceMode.ON -> when {
            missing == FIREBASE_BUILD_TYPES ->
                throw InvalidAppConfigException(listOf("$label: firebase.enabled=true needs $configFileName"))
            missing.isNotEmpty() -> throw InvalidAppConfigException(
                listOf("$label: firebase.enabled=true needs $configFileName for ${missing.sorted().joinToString(", ")}"),
            )
            !hasAppId -> throw InvalidAppConfigException(
                listOf("$label: firebase.enabled=true needs $adsAppIdKey (the AdMob app ID; Google's consent SDK requires it)"),
            )
            else -> FirebaseSetup("implementation", true, mapOf("debug" to "on", "release" to "on"))
        }
        ServiceMode.DEBUG ->
            if ("debug" in configFileBuildTypes && hasAppId) FirebaseSetup(
                "debugImplementation", true, mapOf("debug" to "on", "release" to "off"), RELEASE_FIREBASE_TASKS,
            )
            else FirebaseSetup("debugImplementation", false, mapOf("debug" to "fake", "release" to "off"))
    }
}

/** [firebaseSetup] for one config file: [configFileExists] covers every build type, otherwise none. */
fun firebaseSetup(
    mode: ServiceMode,
    configFileExists: Boolean,
    adsAppId: String? = null,
    label: String = "app",
    configFileName: String = "google-services.json",
    adsAppIdKey: String = "ads.android.appId",
): FirebaseSetup = firebaseSetup(
    mode, if (configFileExists) FIREBASE_BUILD_TYPES else emptySet(), adsAppId, label, configFileName, adsAppIdKey,
)

/**
 * The build types an Android app module at [projectDir] has a `google-services.json` for: the file at
 * the module root covers every build type, `src/<buildType>/google-services.json` covers that build
 * type (the google-services plugin's own lookup).
 */
fun androidFirebaseConfigBuildTypes(projectDir: File): Set<String> =
    if (projectDir.resolve("google-services.json").isFile) FIREBASE_BUILD_TYPES
    else FIREBASE_BUILD_TYPES.filterTo(linkedSetOf()) { projectDir.resolve("src/$it/google-services.json").isFile }

/** The `admobAppId` manifest placeholder: the app's AdMob app ID, or an empty string so merging never fails. */
fun admobManifestPlaceholder(adsAppId: String?): String = adsAppId.orEmpty()

/** The release variant's Firebase Gradle tasks (AGP 8.13, crashlytics-gradle 3.0.8). */
private val RELEASE_FIREBASE_TASKS = listOf(
    "processReleaseGoogleServices",
    "injectCrashlyticsMappingFileIdRelease",
    "injectCrashlyticsVersionControlInfoRelease",
    "uploadCrashlyticsMappingFileRelease",
)
