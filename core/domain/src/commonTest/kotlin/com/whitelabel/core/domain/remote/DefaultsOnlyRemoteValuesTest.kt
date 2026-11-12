package com.whitelabel.core.domain.remote

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test

class DefaultsOnlyRemoteValuesTest {

    @Test fun `every read returns the caller's default`() = runTest {
        for (key in listOf("a", "", "x".repeat(300))) {
            assertThat(DefaultsOnlyRemoteValues.long(key, 7L)).isEqualTo(7L)
            assertThat(DefaultsOnlyRemoteValues.double(key, 1.5)).isEqualTo(1.5)
            assertThat(DefaultsOnlyRemoteValues.string(key, "d")).isEqualTo("d")
            assertThat(DefaultsOnlyRemoteValues.boolean(key, true)).isEqualTo(true)
            assertThat(DefaultsOnlyRemoteValues.json(key, listOf(1), ListSerializer(Int.serializer())))
                .isEqualTo(listOf(1))
        }
        assertThat(DefaultsOnlyRemoteValues.changes.toList()).isEmpty()
    }
}
