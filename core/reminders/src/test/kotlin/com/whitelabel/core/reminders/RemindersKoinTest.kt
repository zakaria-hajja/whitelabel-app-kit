package com.whitelabel.core.reminders

import android.content.Context
import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class RemindersKoinTest {

    @Test fun `reminders module resolves with only a context`() {
        remindersModule().verify(extraTypes = listOf(Context::class))
    }
}
