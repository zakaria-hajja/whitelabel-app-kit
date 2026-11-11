package com.whitelabel.buildlogic

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class KmpNamespaceTest {

    @Test fun `a core module maps to its package`() {
        assertThat(kmpNamespace(":core:common")).isEqualTo("com.whitelabel.core.common")
    }

    @Test fun `a nested shared module keeps every segment`() {
        assertThat(kmpNamespace(":feature:settings:shared")).isEqualTo("com.whitelabel.feature.settings.shared")
        assertThat(kmpNamespace(":apps:showcase:shared")).isEqualTo("com.whitelabel.apps.showcase.shared")
    }

    @Test fun `dashes are dropped, since they are not valid in a package name`() {
        assertThat(kmpNamespace(":core:reminders-x")).isEqualTo("com.whitelabel.core.remindersx")
    }
}
