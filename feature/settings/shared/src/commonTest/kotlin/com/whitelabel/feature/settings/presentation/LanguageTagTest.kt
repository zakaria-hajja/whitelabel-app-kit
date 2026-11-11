package com.whitelabel.feature.settings.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

/** The app offers languages, not regions: a BCP 47 tag's primary language, as java.util.Locale gave it. */
class LanguageTagTest {

    @Test fun `a region is dropped`() {
        assertThat(primaryLanguage("fr-CA")).isEqualTo("fr")
    }

    @Test fun `a script and region are dropped`() {
        assertThat(primaryLanguage("zh-Hant-TW")).isEqualTo("zh")
    }

    @Test fun `an underscore form is read too`() {
        assertThat(primaryLanguage("en_US")).isEqualTo("en")
    }

    @Test fun `the language is lowercased`() {
        assertThat(primaryLanguage("FR")).isEqualTo("fr")
    }

    @Test fun `empty and null follow the device`() {
        assertThat(primaryLanguage("")).isNull()
        assertThat(primaryLanguage(null)).isNull()
    }
}
