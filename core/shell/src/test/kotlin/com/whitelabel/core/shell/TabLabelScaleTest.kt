package com.whitelabel.core.shell

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/**
 * A bottom-bar label may wrap between words, but never inside one ("Calenda/r", bug 8zZ4HDYM).
 * [breaksMidWord] gets the offsets where each line but the last ends.
 */
class TabLabelScaleTest {

    @Test fun `one line never breaks a word`() {
        assertThat(breaksMidWord("Calendar", lineEnds = emptyList())).isEqualTo(false)
    }

    @Test fun `a break inside a word is caught`() {
        assertThat(breaksMidWord("Calendar", lineEnds = listOf(7))).isEqualTo(true)
    }

    @Test fun `a break after a space is between words`() {
        assertThat(breaksMidWord("Past cycles", lineEnds = listOf(5))).isEqualTo(false)
    }

    @Test fun `a break after a hyphen is between words`() {
        assertThat(breaksMidWord("Check-in", lineEnds = listOf(6))).isEqualTo(false)
    }

    @Test fun `a later mid-word break is caught too`() {
        assertThat(breaksMidWord("Past cycles log", lineEnds = listOf(5, 9))).isEqualTo(true)
    }

    @Test fun `a break between emoji and a word is not inside a word`() {
        assertThat(breaksMidWord("🌸 Garden", lineEnds = listOf(3))).isEqualTo(false)
    }
}
