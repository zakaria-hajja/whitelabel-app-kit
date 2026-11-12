package com.whitelabel.core.firebase.google

import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.jupiter.api.Test

class CrashRuleTest {
    private val calls = mutableListOf<String>()
    private val rule = CrashRule(object : CrashCollection {
        override fun setEnabled(enabled: Boolean) { calls += "enabled=$enabled" }
        override fun deleteUnsentReports() { calls += "delete" }
    })

    @Test fun `turning crashes on deletes the queued reports first`() {
        rule.apply(true)
        assertThat(calls).containsExactly("delete", "enabled=true")
    }

    @Test fun `staying on does not delete again`() {
        rule.apply(true)
        calls.clear()
        rule.apply(true)
        assertThat(calls).containsExactly("enabled=true")
    }

    @Test fun `turning crashes off deletes the queued reports`() {
        rule.apply(true)
        calls.clear()
        rule.apply(false)
        assertThat(calls).containsExactly("delete", "enabled=false")
    }

    @Test fun `off at the start deletes too`() {
        rule.apply(false)
        assertThat(calls).containsExactly("delete", "enabled=false")
    }
}
