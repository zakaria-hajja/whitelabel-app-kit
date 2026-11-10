package com.whitelabel.buildlogic.apps

import java.io.File

/** Release signing for one app, from its gitignored `signing.properties`. */
data class AppSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)
