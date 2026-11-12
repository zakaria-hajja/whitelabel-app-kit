package com.whitelabel.core.firebase

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test

class SourceRemoteValuesTest {
    private fun values(vararg pairs: Pair<String, String>) =
        SourceRemoteValues(FakeRemoteConfigSource(mapOf(*pairs)), TestScope())

    @Test fun `typed reads`() {
        val v = values("l" to "42", "d" to "1.5", "s" to "hi", "b" to "true")
        assertThat(v.long("l", 0)).isEqualTo(42L)
        assertThat(v.double("d", 0.0)).isEqualTo(1.5)
        assertThat(v.string("s", "x")).isEqualTo("hi")
        assertThat(v.boolean("b", false)).isEqualTo(true)
    }

    @Test fun `boolean accepts true or 1 and false or 0 trimmed and case-insensitive`() {
        val v = values("a" to "TRUE", "b" to " 1 ", "c" to "false", "d" to " 0 ", "e" to "yes")
        assertThat(v.boolean("a", false)).isEqualTo(true)
        assertThat(v.boolean("b", false)).isEqualTo(true)
        assertThat(v.boolean("c", true)).isEqualTo(false)
        assertThat(v.boolean("d", true)).isEqualTo(false)
        assertThat(v.boolean("e", true)).isEqualTo(true)
        assertThat(v.boolean("e", false)).isEqualTo(false)
    }

    @Test fun `long overflow and decimals return the default`() {
        val v = values("big" to "99999999999999999999", "dec" to "1.5")
        assertThat(v.long("big", 7)).isEqualTo(7L)
        assertThat(v.long("dec", 7)).isEqualTo(7L)
    }

    @Test fun `garbled or wrong-shape json returns the default`() {
        val v = values("bad" to "{", "arr" to "[\"a\"]", "ok" to "[1,2]")
        val ser = ListSerializer(Int.serializer())
        assertThat(v.json("bad", listOf(9), ser)).isEqualTo(listOf(9))
        assertThat(v.json("arr", listOf(9), ser)).isEqualTo(listOf(9))
        assertThat(v.json("ok", listOf(9), ser)).isEqualTo(listOf(1, 2))
    }

    @Test fun `missing and empty values return the default`() {
        val v = values("e" to "")
        assertThat(v.long("e", 5)).isEqualTo(5L)
        assertThat(v.double("e", 2.5)).isEqualTo(2.5)
        assertThat(v.string("e", "d")).isEqualTo("d")
        assertThat(v.boolean("e", true)).isEqualTo(true)
        assertThat(v.string("missing", "d")).isEqualTo("d")
        assertThat(v.json("e", listOf(9), ListSerializer(Int.serializer()))).isEqualTo(listOf(9))
    }

    @Test fun `changes emits after an activation that changed values and not otherwise`() = kotlinx.coroutines.test.runTest {
        val results = ArrayDeque(listOf(true, false))
        var calls = 0
        val source = object : RemoteConfigSource {
            override fun raw(key: String): String? = null
            override fun fetchAndActivate(onDone: (Boolean) -> Unit) {
                calls++
                onDone(results.removeFirst())
            }
        }
        val v = SourceRemoteValues(source, this)
        v.changes.test {
            v.start()
            testScheduler.advanceUntilIdle()
            awaitItem()
            v.start()
            testScheduler.advanceUntilIdle()
            assertThat(calls).isEqualTo(2)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `a throwing source counts as unchanged and does not escape`() = kotlinx.coroutines.test.runTest {
        var calls = 0
        val source = object : RemoteConfigSource {
            override fun raw(key: String): String? = null
            override fun fetchAndActivate(onDone: (Boolean) -> Unit) {
                calls++
                error("boom")
            }
        }
        val v = SourceRemoteValues(source, this)
        v.changes.test {
            v.start()
            testScheduler.advanceUntilIdle()
            assertThat(calls).isEqualTo(1)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
