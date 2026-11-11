package com.whitelabel.buildlogic

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo

class ComposeTranslationsTest {

    private val english = """
        <resources>
            <string name="greeting">Hello</string>
            <string name="farewell">Bye</string>
            <plurals name="items"><item quantity="one">%d item</item></plurals>
        </resources>
    """.trimIndent()

    @org.junit.jupiter.api.Test fun `a complete translation has nothing missing`() {
        val french = """<resources><string name="greeting">Bonjour</string><string name="farewell">Salut</string><plurals name="items"><item quantity="one">%d objet</item></plurals></resources>"""
        assertThat(missingTranslations(english, mapOf("values-fr" to french))).isEmpty()
    }

    @org.junit.jupiter.api.Test fun `a missing string and a missing plural are reported per language`() {
        val french = """<resources><string name="greeting">Bonjour</string></resources>"""
        assertThat(missingTranslations(english, mapOf("values-fr" to french)))
            .isEqualTo(mapOf("values-fr" to listOf("farewell", "items")))
    }

    @org.junit.jupiter.api.Test fun `a string marked translatable false needs no translation`() {
        val withBrand = """<resources><string name="greeting">Hello</string><string name="brand" translatable="false">Acme</string></resources>"""
        val french = """<resources><string name="greeting">Bonjour</string></resources>"""
        assertThat(missingTranslations(withBrand, mapOf("values-fr" to french))).isEmpty()
    }
}
