package com.whitelabel.buildlogic.apps

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FirebaseSetupTest {

    private companion object {
        const val ID = "ca-app-pub-1234567890123456~1234567890"
    }

    @Test fun `off needs nothing and turns firebase off in both build types`() {
        assertThat(firebaseSetup(ServiceMode.OFF, configFileExists = false)).isEqualTo(
            FirebaseSetup(null, false, mapOf("debug" to "off", "release" to "off")),
        )
        assertThat(firebaseSetup(ServiceMode.OFF, configFileExists = true).dependencyConfiguration).isEqualTo(null)
    }

    @Test fun `on with the file uses implementation and the plugins`() {
        assertThat(firebaseSetup(ServiceMode.ON, configFileExists = true, adsAppId = ID)).isEqualTo(
            FirebaseSetup("implementation", true, mapOf("debug" to "on", "release" to "on")),
        )
    }

    @Test fun `on without the file fails naming the app and the file`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            firebaseSetup(ServiceMode.ON, configFileExists = false, adsAppId = ID, label = "apps/sample")
        }

        assertThat(e.problems).isEqualTo(listOf("apps/sample: firebase.enabled=true needs google-services.json"))
        assertThat(e.message!!).contains("firebase.enabled=true needs google-services.json")
    }

    @Test fun `on without the file names the given config file`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            firebaseSetup(ServiceMode.ON, configFileExists = false, adsAppId = ID, configFileName = "GoogleService-Info.plist")
        }

        assertThat(e.problems.single()).isEqualTo("app: firebase.enabled=true needs GoogleService-Info.plist")
    }

    @Test fun `debug with the file is real in debug only`() {
        assertThat(firebaseSetup(ServiceMode.DEBUG, configFileExists = true, adsAppId = ID).copy(disabledTasks = emptyList())).isEqualTo(
            FirebaseSetup("debugImplementation", true, mapOf("debug" to "on", "release" to "off")),
        )
    }

    @Test fun `debug without the file falls back to the fake in debug`() {
        assertThat(firebaseSetup(ServiceMode.DEBUG, configFileExists = false)).isEqualTo(
            FirebaseSetup("debugImplementation", false, mapOf("debug" to "fake", "release" to "off")),
        )
    }

    @Test fun `debug with the file skips release google-services processing`() {
        assertThat(firebaseSetup(ServiceMode.DEBUG, configFileExists = true, adsAppId = ID).disabledTasks)
            .contains("processReleaseGoogleServices")
    }

    @Test fun `debug without the file and the other modes disable nothing`() {
        assertThat(firebaseSetup(ServiceMode.DEBUG, configFileExists = false).disabledTasks).isEqualTo(emptyList())
        assertThat(firebaseSetup(ServiceMode.ON, configFileExists = true, adsAppId = ID).disabledTasks).isEqualTo(emptyList())
        assertThat(firebaseSetup(ServiceMode.OFF, configFileExists = false).disabledTasks).isEqualTo(emptyList())
    }

    @Test fun `on without the app id fails naming the key and why`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            firebaseSetup(ServiceMode.ON, configFileExists = true, adsAppId = null, label = "apps/sample")
        }

        assertThat(e.problems).isEqualTo(
            listOf("apps/sample: firebase.enabled=true needs ads.android.appId (the AdMob app ID; Google's consent SDK requires it)"),
        )
    }

    @Test fun `on without the app id names the given key`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            firebaseSetup(ServiceMode.ON, configFileExists = true, adsAppId = null, adsAppIdKey = "ads.ios.appId")
        }

        assertThat(e.problems.single()).contains("firebase.enabled=true needs ads.ios.appId (the AdMob app ID")
    }

    @Test fun `on with a blank app id fails too`() {
        assertThrows(InvalidAppConfigException::class.java) {
            firebaseSetup(ServiceMode.ON, configFileExists = true, adsAppId = "")
        }
    }

    @Test fun `on reports a missing file before a missing app id`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            firebaseSetup(ServiceMode.ON, configFileExists = false, adsAppId = null)
        }

        assertThat(e.problems.single()).contains("needs google-services.json")
    }

    @Test fun `debug with the file and the app id is real in debug`() {
        assertThat(firebaseSetup(ServiceMode.DEBUG, configFileExists = true, adsAppId = ID).modeFor)
            .isEqualTo(mapOf("debug" to "on", "release" to "off"))
    }

    @Test fun `debug with the file but no app id falls back to the fake in debug`() {
        assertThat(firebaseSetup(ServiceMode.DEBUG, configFileExists = true, adsAppId = null)).isEqualTo(
            FirebaseSetup("debugImplementation", false, mapOf("debug" to "fake", "release" to "off")),
        )
    }

    @Test fun `debug with the app id but no file falls back to the fake in debug`() {
        assertThat(firebaseSetup(ServiceMode.DEBUG, configFileExists = false, adsAppId = ID).modeFor)
            .isEqualTo(mapOf("debug" to "fake", "release" to "off"))
    }

    @Test fun `off ignores the app id`() {
        assertThat(firebaseSetup(ServiceMode.OFF, configFileExists = false, adsAppId = ID).modeFor)
            .isEqualTo(mapOf("debug" to "off", "release" to "off"))
    }

    @Test fun `the manifest placeholder is the app id or an empty string`() {
        assertThat(admobManifestPlaceholder(ID)).isEqualTo(ID)
        assertThat(admobManifestPlaceholder(null)).isEqualTo("")
    }

    // One config file per build type.

    @Test fun `on with a file per build type uses implementation and the plugins`() {
        assertThat(firebaseSetup(ServiceMode.ON, setOf("debug", "release"), adsAppId = ID)).isEqualTo(
            FirebaseSetup("implementation", true, mapOf("debug" to "on", "release" to "on")),
        )
    }

    @Test fun `on with only the debug file fails naming release`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            firebaseSetup(ServiceMode.ON, setOf("debug"), adsAppId = ID, label = "apps/sample")
        }

        assertThat(e.problems).isEqualTo(listOf("apps/sample: firebase.enabled=true needs google-services.json for release"))
    }

    @Test fun `on with only the release file fails naming debug`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            firebaseSetup(
                ServiceMode.ON, setOf("release"), adsAppId = ID, label = "apps/sample",
                configFileName = "GoogleService-Info.plist",
            )
        }

        assertThat(e.problems).isEqualTo(listOf("apps/sample: firebase.enabled=true needs GoogleService-Info.plist for debug"))
    }

    @Test fun `on with no file at all keeps the message without build types`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            firebaseSetup(ServiceMode.ON, emptySet(), adsAppId = ID, label = "apps/sample")
        }

        assertThat(e.problems).isEqualTo(listOf("apps/sample: firebase.enabled=true needs google-services.json"))
    }

    @Test fun `on with a partial set reports the missing file before a missing app id`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            firebaseSetup(ServiceMode.ON, setOf("debug"), adsAppId = null)
        }

        assertThat(e.problems.single()).contains("needs google-services.json for release")
    }

    @Test fun `debug mode with only a release file is fake in debug`() {
        val setup = firebaseSetup(ServiceMode.DEBUG, setOf("release"), adsAppId = ID)

        assertThat(setup.modeFor).isEqualTo(mapOf("debug" to "fake", "release" to "off"))
        assertThat(setup.applyGradlePlugins).isFalse()
    }

    @Test fun `debug mode with a debug file is real in debug and skips release tasks`() {
        val setup = firebaseSetup(ServiceMode.DEBUG, setOf("debug"), adsAppId = ID)

        assertThat(setup.applyGradlePlugins).isTrue()
        assertThat(setup.modeFor).isEqualTo(mapOf("debug" to "on", "release" to "off"))
        assertThat(setup.disabledTasks).contains("processReleaseGoogleServices")
    }

    @Test fun `the boolean overload means every build type or none`() {
        assertThat(firebaseSetup(ServiceMode.ON, configFileExists = true, adsAppId = ID))
            .isEqualTo(firebaseSetup(ServiceMode.ON, FIREBASE_BUILD_TYPES, adsAppId = ID))
        assertThat(firebaseSetup(ServiceMode.DEBUG, configFileExists = false, adsAppId = ID))
            .isEqualTo(firebaseSetup(ServiceMode.DEBUG, emptySet(), adsAppId = ID))
    }

    // Android config file lookup.

    @TempDir
    lateinit var moduleDir: File

    private fun file(path: String) = File(moduleDir, path).apply { parentFile.mkdirs(); writeText("{}") }

    @Test fun `no android config file covers no build type`() {
        assertThat(androidFirebaseConfigBuildTypes(moduleDir)).isEqualTo(emptySet())
    }

    @Test fun `the root android config file covers every build type`() {
        file("google-services.json")

        assertThat(androidFirebaseConfigBuildTypes(moduleDir)).isEqualTo(setOf("debug", "release"))
    }

    @Test fun `a build type's own android config file covers only that build type`() {
        file("src/debug/google-services.json")

        assertThat(androidFirebaseConfigBuildTypes(moduleDir)).isEqualTo(setOf("debug"))
    }

    @Test fun `an android config file per build type covers both`() {
        file("src/debug/google-services.json")
        file("src/release/google-services.json")

        assertThat(androidFirebaseConfigBuildTypes(moduleDir)).isEqualTo(setOf("debug", "release"))
    }

    @Test fun `a directory named like the android config file does not count`() {
        File(moduleDir, "google-services.json").mkdirs()
        File(moduleDir, "src/release/google-services.json").mkdirs()

        assertThat(androidFirebaseConfigBuildTypes(moduleDir)).isEqualTo(emptySet())
    }
}
