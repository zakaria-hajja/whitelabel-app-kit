package com.whitelabel.core.shell

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Test
import org.w3c.dom.Element

/**
 * App data in JsonStore files is backed up so a reinstall or a new phone restores it; the session
 * (tokens encrypted with a Keystore key that can't be restored) and the signed-in user never are.
 */
class BackupRulesTest {

    private fun rules(name: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/xml/$name.xml")).documentElement

    private fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.rule(tag: String): List<String> =
        children(tag).map { "${it.getAttribute("domain")}:${it.getAttribute("path")}" }

    @Test fun `cloud backup and device transfer include only datastore`() {
        val root = rules("data_extraction_rules")
        listOf("cloud-backup", "device-transfer").forEach { section ->
            val rules = root.children(section).single()
            assertThat(rules.rule("include")).containsExactly("file:datastore/")
            assertThat(rules.rule("exclude"))
                .containsExactlyInAnyOrder("file:datastore/session.json", "file:datastore/user.json")
        }
    }

    @Test fun `legacy backup rules match`() {
        val root = rules("backup_rules")
        assertThat(root.rule("include")).containsExactly("file:datastore/")
        assertThat(root.rule("exclude"))
            .containsExactlyInAnyOrder("file:datastore/session.json", "file:datastore/user.json")
    }
}
