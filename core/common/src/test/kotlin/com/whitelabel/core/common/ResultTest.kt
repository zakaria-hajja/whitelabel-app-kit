package com.whitelabel.core.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import org.junit.jupiter.api.Test

class ResultTest {

    @Test
    fun `map transforms success and passes error through`() {
        assertThat(Result.Success(2).map { it * 3 }).isEqualTo(Result.Success(6))
        val err: Result<Int, DataError> = Result.Error(DataError.Network.NO_INTERNET)
        assertThat(err.map { it * 3 }).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
    }

    @Test
    fun `onSuccess and onFailure run only on their branch and return the same instance`() {
        val success: Result<Int, DataError> = Result.Success(1)
        val failure: Result<Int, DataError> = Result.Error(DataError.Local.NOT_FOUND)
        val seenData = mutableListOf<Int>()
        val seenErrors = mutableListOf<DataError>()

        val s = success.onSuccess { seenData += it }.onFailure { seenErrors += it }
        val f = failure.onSuccess { seenData += it }.onFailure { seenErrors += it }

        assertThat(seenData).isEqualTo(listOf(1))
        assertThat(seenErrors).isEqualTo(listOf<DataError>(DataError.Local.NOT_FOUND))
        assertThat(s).isSameInstanceAs(success)
        assertThat(f).isSameInstanceAs(failure)
    }

    @Test
    fun `asEmptyResult keeps the error`() {
        val ok: Result<Int, DataError> = Result.Success(5)
        val err: Result<Int, DataError> = Result.Error(DataError.Network.REQUEST_TIMEOUT)

        assertThat(ok.asEmptyResult()).isEqualTo(Result.Success(Unit))
        assertThat(err.asEmptyResult()).isEqualTo(Result.Error(DataError.Network.REQUEST_TIMEOUT))
    }
}
