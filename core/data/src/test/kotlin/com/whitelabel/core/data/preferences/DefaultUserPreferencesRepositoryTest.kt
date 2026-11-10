package com.whitelabel.core.data.preferences

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.whitelabel.core.domain.ThemePreference
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DefaultUserPreferencesRepositoryTest {

    @TempDir
    lateinit var dir: File

    private suspend fun <R> withRepository(block: suspend (DefaultUserPreferencesRepository) -> R): R {
        val job = Job()
        return try {
            block(DefaultUserPreferencesRepository(File(dir, "preferences.json"), CoroutineScope(Dispatchers.IO + job)))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun `theme is null until chosen then persisted`() = runTest {
        assertThat(withRepository { it.theme.first() }).isNull()
        withRepository { it.setTheme(ThemePreference.DARK) }
        assertThat(withRepository { it.theme.first() }).isEqualTo(ThemePreference.DARK)
    }
}
