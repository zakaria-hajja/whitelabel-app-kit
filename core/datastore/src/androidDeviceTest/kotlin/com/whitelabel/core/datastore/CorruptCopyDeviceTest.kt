package com.whitelabel.core.datastore

import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

/** The corrupt copy on a device's real file system, in the conventional store location. */
class CorruptCopyDeviceTest {

    @Serializable private data class Prefs(val theme: String? = null)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = context.jsonStoreFile("corrupt_copy_test")
    private val copy = File(file.parentFile, "${file.name}.corrupt")

    @AfterTest fun cleanUp() {
        listOf(file, copy, File(file.parentFile, "${file.name}.tmp"), File(file.parentFile, "${copy.name}.tmp")).forEach { it.delete() }
    }

    private fun corrupt(bytes: ByteArray) {
        file.parentFile!!.mkdirs()
        file.writeBytes(bytes)
    }

    private fun readFreshStore(policy: CorruptFilePolicy = CorruptFilePolicy.KEEP_COPY_THEN_DEFAULT): Prefs = runBlocking {
        val job = Job()
        val store = JsonStore(file, Prefs.serializer(), Prefs(), CoroutineScope(Dispatchers.IO + job), policy)
        try {
            store.data.first()
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun keepsTheOriginalAndWritesTheDefault() {
        val original = "{\"theme\":\"DA".toByteArray() + 0xFF.toByte()
        corrupt(original)

        assertThat(readFreshStore()).isEqualTo(Prefs())
        assertThat(copy.readBytes().toList()).isEqualTo(original.toList())
        assertThat(file.readText()).isEqualTo("{}")
    }

    @Test fun aNewerCorruptionReplacesTheCopy() {
        corrupt("{first".toByteArray())
        readFreshStore()
        corrupt("{second".toByteArray())
        readFreshStore()

        assertThat(copy.readText()).isEqualTo("{second")
    }

    @Test fun fileOverloadPassesThePolicy() {
        corrupt("{not json".toByteArray())

        assertFailsWith<CorruptStoreException> { readFreshStore(CorruptFilePolicy.FAIL) }
        assertThat(file.readText()).isEqualTo("{not json")
        assertThat(copy.exists()).isFalse()
    }
}
