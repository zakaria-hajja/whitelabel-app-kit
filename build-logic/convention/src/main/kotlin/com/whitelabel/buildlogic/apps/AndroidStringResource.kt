package com.whitelabel.buildlogic.apps

/** Escapes [raw] so aapt2 reads it back verbatim as the value of a `<string>` resource. */
fun androidStringResource(raw: String): String {
    val escaped = buildString {
        for (c in raw) {
            when (c) {
                '\\' -> append("\\\\")
                '\'' -> append("\\'")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }
    return if (escaped.startsWith("@") || escaped.startsWith("?")) "\\$escaped" else escaped
}
