package com.whitelabel.core.firebase.google

import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.jupiter.api.Test

class CrashRuleTest {
    private val calls = mutableListOf<String>()
    private var persisted = false
    private val rule = CrashRule(object : CrashCollection {
        override fun isEnabled() = persisted
        override fun setEnabled(enabled: Boolean) { calls += "enabled=$enabled"; persisted = enabled }
        override fun deleteUnsentReports() { calls += "delete" }
    })

    @Test fun `first turning on drops the reports captured while off`() {
        rule.apply(true)
        assertThat(calls).containsExactly("delete", "enabled=true")
    }

    @Test fun `already on from an earlier launch keeps its reports`() {
        persisted = true
        rule.apply(true)
        assertThat(calls).containsExactly("enabled=true")
    }

    @Test fun `turning off deletes nothing`() {
        persisted = true
        rule.apply(false)
        assertThat(calls).containsExactly("enabled=false")
    }

    @Test fun `turning on again after off drops what was captured while off`() {
        persisted = true
        rule.apply(false)
        calls.clear()
        rule.apply(true)
        assertThat(calls).containsExactly("delete", "enabled=true")
    }
}
