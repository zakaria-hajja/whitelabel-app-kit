package com.whitelabel.core.network

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import java.nio.channels.UnresolvedAddressException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test

/** Failures only the JVM engines (OkHttp on Android) throw. */
class SafeCallJvmTest {

    @Serializable private data class Health(val status: String)

    @Test fun `unresolved address maps to NO_INTERNET`() = runTest {
        val client = testClient { throw UnresolvedAddressException() }
        assertThat(client.getResult<Health>("health")).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
    }
}
