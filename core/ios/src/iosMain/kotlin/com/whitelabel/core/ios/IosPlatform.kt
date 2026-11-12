package com.whitelabel.core.ios

import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.datastore.StorePaths
import com.whitelabel.core.datastore.TokenCipher
import com.whitelabel.core.datastore.iosJsonStorePath
import com.whitelabel.core.domain.LanguageRepository

/**
 * What the iOS app provides to the shared code. [tokenCipher], [language] and [storePaths] are
 * implemented in Swift (WhitelabelKit's `PlatformFactory`); [brand] comes from the app's generated
 * config; [logger] receives log lines (outside PRODUCTION) and analytics. [firebase] is the app's
 * `WL_FIREBASE` setting: `on`, `fake` or `off`.
 */
class IosPlatform(
    val tokenCipher: TokenCipher,
    val language: LanguageRepository,
    val storePaths: StorePaths,
    val brand: BrandConfig,
    val logger: (String) -> Unit,
    val firebase: String = "off",
) {
    /** With the stores at their conventional place: Application Support, `datastore/<name>.json`. */
    constructor(tokenCipher: TokenCipher, language: LanguageRepository, brand: BrandConfig, logger: (String) -> Unit) :
        this(tokenCipher, language, StorePaths { iosJsonStorePath(it) }, brand, logger, firebase = "off")

    /** As above, with the app's `WL_FIREBASE` setting (`PlatformFactory` passes it). */
    constructor(tokenCipher: TokenCipher, language: LanguageRepository, brand: BrandConfig, logger: (String) -> Unit, firebase: String) :
        this(tokenCipher, language, StorePaths { iosJsonStorePath(it) }, brand, logger, firebase)
}
