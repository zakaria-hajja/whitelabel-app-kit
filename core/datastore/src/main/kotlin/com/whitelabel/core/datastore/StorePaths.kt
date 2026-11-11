package com.whitelabel.core.datastore

import java.io.File

/** Where the [JsonStore] document called `name` lives on this platform. */
fun interface StorePaths {
    fun file(name: String): File
}
