package com.whitelabel.feature.authentication

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.common.Result
import com.whitelabel.core.testing.ContractBackend
import com.whitelabel.feature.authentication.data.KtorPasswordResetDataSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ContractTest {
    private val backend = ContractBackend()
    private val source = KtorPasswordResetDataSource(backend.client(ContractBackend.SignedOut))

    @Test fun `post api v1 auth forgot-password matches the contract`() = runTest {
        assertThat(source.requestReset("ada@example.com")).isEqualTo(Result.Success(Unit))
        backend.assertRequestMatches("post", "/api/v1/auth/forgot-password")
    }

    @Test fun `post api v1 auth reset-password matches the contract`() = runTest {
        assertThat(source.reset("a-token", "0123456789")).isEqualTo(Result.Success(Unit))
        backend.assertRequestMatches("post", "/api/v1/auth/reset-password")
    }
}
