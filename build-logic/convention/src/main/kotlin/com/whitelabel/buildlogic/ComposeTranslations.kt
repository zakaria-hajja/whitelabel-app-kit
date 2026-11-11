package com.whitelabel.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

private val namedResource = Regex("""<(string|plurals)\s+name="([^"]+)"([^>]*)>""")

/**
 * The string and plural names of [defaultXml] (`values/strings.xml`) that each translation in
 * [translatedXml] (`values-fr` → its XML) lacks, in declaration order. Names marked
 * `translatable="false"` need no translation. Languages missing nothing are left out.
 */
internal fun missingTranslations(defaultXml: String, translatedXml: Map<String, String>): Map<String, List<String>> {
    val required = namedResource.findAll(defaultXml)
        .filterNot { it.groupValues[3].contains("""translatable="false"""") }
        .map { it.groupValues[2] }
        .toList()
    return translatedXml.mapValues { (_, xml) ->
        val present = namedResource.findAll(xml).map { it.groupValues[2] }.toSet()
        required.filterNot { it in present }
    }.filterValues { it.isNotEmpty() }
}

/**
 * Fails when a translation in `composeResources` lacks a string the default language has. Android
 * lint checks this for `res/`; nothing checks Compose resources, so this task does (part of `check`).
 */
abstract class CheckComposeTranslations : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourcesDir: DirectoryProperty

    @TaskAction
    fun check() {
        val root = resourcesDir.orNull?.asFile ?: return
        val default = root.resolve("values/strings.xml").takeIf { it.isFile } ?: return
        val translations = root.listFiles { file -> file.isDirectory && file.name.startsWith("values-") }.orEmpty()
            .associate { dir -> dir.name to (dir.resolve("strings.xml").takeIf { it.isFile }?.readText() ?: "") }
        val missing = missingTranslations(default.readText(), translations)
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing translations in ${root.path}:\n" +
                    missing.entries.joinToString("\n") { (language, names) -> "  $language: ${names.joinToString()}" },
            )
        }
    }
}
