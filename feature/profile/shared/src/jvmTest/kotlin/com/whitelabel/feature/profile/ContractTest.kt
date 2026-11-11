package com.whitelabel.feature.profile

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.common.Result
import com.whitelabel.core.testing.ContractBackend
import com.whitelabel.feature.profile.data.KtorPasswordDataSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ContractTest {
    @Test fun `patch api v1 users me password matches the contract`() = runTest {
        val backend = ContractBackend()
        assertThat(KtorPasswordDataSource(backend.client()).change("old pass", "0123456789")).isEqualTo(Result.Success(Unit))
        backend.assertRequestMatches("patch", "/api/v1/users/me/password")
    }
}
