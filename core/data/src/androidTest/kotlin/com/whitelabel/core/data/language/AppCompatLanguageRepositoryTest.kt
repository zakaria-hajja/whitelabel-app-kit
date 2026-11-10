package com.whitelabel.core.data.language

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppCompatLanguageRepositoryTest {

    private val repository = AppCompatLanguageRepository(InstrumentationRegistry.getInstrumentation().targetContext)

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @After fun reset() = onMain { repository.set(null) }

    @Test fun set_fr_thenCurrentIsFr() {
        onMain { repository.set("fr") }
        onMain { assertThat(repository.current()).isEqualTo("fr") }
    }

    @Test fun set_null_thenCurrentIsNull() {
        onMain { repository.set("fr") }
        onMain { repository.set(null) }
        onMain { assertThat(repository.current()).isNull() }
    }
}
