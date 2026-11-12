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
 * `WL_FIREBASE` setting: `on`, `fake` or `off`. [ads] is `WL_ADS` (`on` or `off` for this build),
 * [adsUnits] `WL_ADS_UNITS` (`placement=unit;…`), [adsDeclared] `WL_ADS_DECLARED` (`ads.enabled` is
 * set, whatever the build) and [debugBuild] whether this is a Debug build.
 */
class IosPlatform(
    val tokenCipher: TokenCipher,
    val language: LanguageRepository,
    val storePaths: StorePaths,
    val brand: BrandConfig,
    val logger: (String) -> Unit,
    val firebase: String = "off",
    val ads: String = "off",
    val adsUnits: String = "",
    val adsDeclared: Boolean = false,
    val debugBuild: Boolean = false,
) {
    /** With the stores at their conventional place: Application Support, `datastore/<name>.json`. */
    constructor(tokenCipher: TokenCipher, language: LanguageRepository, brand: BrandConfig, logger: (String) -> Unit) :
        this(tokenCipher, language, StorePaths { iosJsonStorePath(it) }, brand, logger, firebase = "off")

    /** As above, with the app's `WL_FIREBASE` setting (`PlatformFactory` passes it). */
    constructor(tokenCipher: TokenCipher, language: LanguageRepository, brand: BrandConfig, logger: (String) -> Unit, firebase: String) :
        this(tokenCipher, language, StorePaths { iosJsonStorePath(it) }, brand, logger, firebase)

    /** As above, with the app's ads settings (`PlatformFactory` passes them). */
    constructor(
        tokenCipher: TokenCipher,
        language: LanguageRepository,
        brand: BrandConfig,
        logger: (String) -> Unit,
        firebase: String,
        ads: String,
        adsUnits: String,
        adsDeclared: Boolean,
        debugBuild: Boolean,
    ) : this(tokenCipher, language, StorePaths { iosJsonStorePath(it) }, brand, logger, firebase, ads, adsUnits, adsDeclared, debugBuild)
}
