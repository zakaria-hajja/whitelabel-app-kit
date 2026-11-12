package com.whitelabel.core.firebase.google

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.whitelabel.core.firebase.FirebaseServicesContributor
import com.whitelabel.core.network.ModuleContributor
import java.util.ServiceLoader
import org.junit.jupiter.api.Test

class FirebaseContributorTest {
    @Test fun `the contributor is discoverable by ServiceLoader`() {
        val found = ServiceLoader.load(ModuleContributor::class.java).map { it::class }
        assertThat(found).contains(FirebaseContributor::class)
    }

    @Test fun `the contributor carries the marker the shell finds it by`() {
        assertThat(FirebaseContributor()).isInstanceOf<FirebaseServicesContributor>()
    }

    @Test fun `debug builds fetch at once and release builds every twelve hours`() {
        assertThat(fetchIntervalSeconds(debuggable = true)).isEqualTo(0L)
        assertThat(fetchIntervalSeconds(debuggable = false)).isEqualTo(43_200L)
    }
}
