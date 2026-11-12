package com.whitelabel.buildlogic.apps

/**
 * What one app gets for Firebase. [dependencyConfiguration] is the Gradle configuration for the
 * Firebase libraries (`null` for none), [applyGradlePlugins] whether the google-services plugins
 * apply, and [modeFor] the mode per build type (`debug`/`release`) written as the
 * `foundation_firebase` string on Android and `WL_FIREBASE` on iOS: `on`, `fake` or `off`.
 */
data class FirebaseSetup(
    val dependencyConfiguration: String?,
    val applyGradlePlugins: Boolean,
    val modeFor: Map<String, String>,
)

/**
 * Decides the Firebase setup from `firebase.enabled` and whether the platform's config file exists.
 * [configFileName] and [label] only shape the error for `true` without the file. In `debug` mode
 * without the file, debug builds use the fake so the app still runs.
 */
fun firebaseSetup(
    mode: ServiceMode,
    configFileExists: Boolean,
    label: String = "app",
    configFileName: String = "google-services.json",
): FirebaseSetup = when (mode) {
    ServiceMode.OFF -> FirebaseSetup(null, false, mapOf("debug" to "off", "release" to "off"))
    ServiceMode.ON ->
        if (configFileExists) FirebaseSetup("implementation", true, mapOf("debug" to "on", "release" to "on"))
        else throw InvalidAppConfigException(listOf("$label: firebase.enabled=true needs $configFileName"))
    ServiceMode.DEBUG ->
        if (configFileExists) FirebaseSetup("debugImplementation", true, mapOf("debug" to "on", "release" to "off"))
        else FirebaseSetup("debugImplementation", false, mapOf("debug" to "fake", "release" to "off"))
}
