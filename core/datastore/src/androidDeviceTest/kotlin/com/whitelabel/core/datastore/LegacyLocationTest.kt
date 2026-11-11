package com.whitelabel.core.datastore

import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

/** Data an Android install wrote before the move to okio is read from the same place. */
class LegacyLocationTest {

    @Serializable private data class Prefs(val theme: String? = null)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val legacy = File(context.filesDir, "datastore/legacy_location_test.json")

    @AfterTest fun cleanUp() {
        legacy.delete()
    }

    @Test fun readsADocumentAtTheOldPath() = runBlocking {
        legacy.parentFile!!.mkdirs()
        legacy.writeText("""{"theme":"DARK"}""")
        val job = Job()
        val store = JsonStore(context.jsonStorePath("legacy_location_test"), Prefs.serializer(), Prefs(), CoroutineScope(Dispatchers.IO + job))
        try {
            assertThat(store.data.first()).isEqualTo(Prefs("DARK"))
        } finally {
            job.cancelAndJoin()
        }
    }
}
