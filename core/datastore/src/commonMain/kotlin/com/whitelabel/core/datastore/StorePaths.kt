package com.whitelabel.core.datastore

import okio.Path

/** Where the [JsonStore] document called `name` lives on this platform. */
fun interface StorePaths {
    fun path(name: String): Path
}
