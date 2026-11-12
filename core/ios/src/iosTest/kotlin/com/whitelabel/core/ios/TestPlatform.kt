package com.whitelabel.core.ios

import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.branding.RuntimeDefaults
import com.whitelabel.core.datastore.Sealed
import com.whitelabel.core.datastore.StorePaths
import com.whitelabel.core.datastore.TokenCipher
import com.whitelabel.core.domain.LanguageRepository
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

fun testDir(): Path = (NSTemporaryDirectory() + "core-ios-" + NSUUID().UUIDString).toPath().also { FileSystem.SYSTEM.createDirectories(it) }

fun testPlatform(dir: Path, apiBaseUrl: String? = "https://example.invalid/api/v1/", firebase: String = "off") = IosPlatform(
    tokenCipher = object : TokenCipher {
        override fun encrypt(plain: ByteArray) = Sealed(ByteArray(12), plain)
        override fun decrypt(sealed: Sealed) = sealed.ciphertext
    },
    language = object : LanguageRepository {
        override fun current(): String? = null
        override fun set(languageTag: String?) = Unit
    },
    storePaths = StorePaths { name -> dir / "$name.json" },
    brand = BrandConfig(
        tenant = null, appName = "Showcase", applicationId = "com.whitelabel.showcase", versionName = "1.0",
        environment = Environment.DEVELOPMENT, apiBaseUrl = apiBaseUrl, deepLinkScheme = "showcase",
        runtimeDefaults = RuntimeDefaults(supportEmail = null, privacyPolicyUrl = "", termsUrl = "", enabledFeatures = emptySet()),
    ),
    logger = {},
    firebase = firebase,
)
