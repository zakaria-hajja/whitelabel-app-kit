package com.whitelabel.core.shell.brand

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import com.whitelabel.core.network.EngineOverride
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import java.util.Optional
import org.junit.jupiter.api.Test

class EngineSelectionTest {

    private val default = MockEngine { respondOk() }
    private val replacement = MockEngine { respondOk() }

    @Test fun `no override uses the default engine`() {
        var built = 0
        val engine = selectEngine(Optional.empty()) { built++; default }
        assertThat(engine).isSameInstanceAs(default)
        assertThat(built).isEqualTo(1)
    }

    @Test fun `an override replaces it`() {
        var built = 0
        val engine = selectEngine(Optional.of(EngineOverride { replacement })) { built++; default }
        assertThat(engine).isSameInstanceAs(replacement)
        assertThat(built).isEqualTo(0)
    }
}
