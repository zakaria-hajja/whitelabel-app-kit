package com.whitelabel.core.shell

import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import com.whitelabel.core.shell.brand.brandConfigFromResources
import org.junit.Test

class BrandResourcesTest {

    /** The shell's own defaults are blank: an app that didn't apply the application plugin must fail loudly. */
    @Test
    fun unconfiguredResources_failNamingAppProperties() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertFailure { brandConfigFromResources(context) }
            .isInstanceOf(IllegalStateException::class)
            .messageContains("app.properties")
    }
}
