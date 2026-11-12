package com.whitelabel.core.consent.google

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.google.android.ump.ConsentInformation
import com.whitelabel.core.consent.UmpStatus
import org.junit.jupiter.api.Test

class UmpStatusMappingTest {
    @Test
    fun `maps UMP status codes`() {
        assertThat(umpStatusOf(ConsentInformation.ConsentStatus.UNKNOWN)).isEqualTo(UmpStatus.UNKNOWN)
        assertThat(umpStatusOf(ConsentInformation.ConsentStatus.REQUIRED)).isEqualTo(UmpStatus.REQUIRED)
        assertThat(umpStatusOf(ConsentInformation.ConsentStatus.NOT_REQUIRED)).isEqualTo(UmpStatus.NOT_REQUIRED)
        assertThat(umpStatusOf(ConsentInformation.ConsentStatus.OBTAINED)).isEqualTo(UmpStatus.OBTAINED)
    }

    @Test
    fun `an unknown code maps to UNKNOWN`() {
        assertThat(umpStatusOf(99)).isEqualTo(UmpStatus.UNKNOWN)
        assertThat(umpStatusOf(-1)).isEqualTo(UmpStatus.UNKNOWN)
    }
}
