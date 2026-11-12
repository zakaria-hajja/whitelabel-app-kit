package com.whitelabel.buildlogic.apps

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FirebaseSetupTest {

    @Test fun `off needs nothing and turns firebase off in both build types`() {
        assertThat(firebaseSetup(ServiceMode.OFF, configFileExists = false)).isEqualTo(
            FirebaseSetup(null, false, mapOf("debug" to "off", "release" to "off")),
        )
        assertThat(firebaseSetup(ServiceMode.OFF, configFileExists = true).dependencyConfiguration).isEqualTo(null)
    }

    @Test fun `on with the file uses implementation and the plugins`() {
        assertThat(firebaseSetup(ServiceMode.ON, configFileExists = true)).isEqualTo(
            FirebaseSetup("implementation", true, mapOf("debug" to "on", "release" to "on")),
        )
    }

    @Test fun `on without the file fails naming the app and the file`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            firebaseSetup(ServiceMode.ON, configFileExists = false, label = "apps/sample")
        }

        assertThat(e.problems).isEqualTo(listOf("apps/sample: firebase.enabled=true needs google-services.json"))
        assertThat(e.message!!).contains("firebase.enabled=true needs google-services.json")
    }

    @Test fun `on without the file names the given config file`() {
        val e = assertThrows(InvalidAppConfigException::class.java) {
            firebaseSetup(ServiceMode.ON, configFileExists = false, configFileName = "GoogleService-Info.plist")
        }

        assertThat(e.problems.single()).isEqualTo("app: firebase.enabled=true needs GoogleService-Info.plist")
    }

    @Test fun `debug with the file is real in debug only`() {
        assertThat(firebaseSetup(ServiceMode.DEBUG, configFileExists = true)).isEqualTo(
            FirebaseSetup("debugImplementation", true, mapOf("debug" to "on", "release" to "off")),
        )
    }

    @Test fun `debug without the file falls back to the fake in debug`() {
        assertThat(firebaseSetup(ServiceMode.DEBUG, configFileExists = false)).isEqualTo(
            FirebaseSetup("debugImplementation", false, mapOf("debug" to "fake", "release" to "off")),
        )
    }
}
