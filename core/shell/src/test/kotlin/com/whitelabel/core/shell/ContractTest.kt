package com.whitelabel.core.shell

import assertk.assertThat
import assertk.assertions.isInstanceOf
import com.whitelabel.core.common.Result
import com.whitelabel.core.shell.diagnostics.KtorHealthDataSource
import com.whitelabel.core.testing.ContractBackend
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ContractTest {
    @Test fun `get api v1 health matches the contract`() = runTest {
        assertThat(KtorHealthDataSource(ContractBackend().client(ContractBackend.SignedOut)).check()).isInstanceOf(Result.Success::class)
    }

    @Test fun `the smallest valid health response still decodes`() = runTest {
        assertThat(KtorHealthDataSource(ContractBackend(minimal = true).client(ContractBackend.SignedOut)).check()).isInstanceOf(Result.Success::class)
    }
}
