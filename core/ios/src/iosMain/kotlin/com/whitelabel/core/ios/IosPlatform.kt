package com.whitelabel.core.ios

import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.datastore.StorePaths
import com.whitelabel.core.datastore.TokenCipher
import com.whitelabel.core.domain.LanguageRepository

/**
 * What the iOS app provides to the shared code. [tokenCipher], [language] and [storePaths] are
 * implemented in Swift (WhitelabelKit's `PlatformFactory`); [brand] comes from the app's generated
 * config; [logger] receives log lines (outside PRODUCTION) and analytics.
 */
class IosPlatform(
    val tokenCipher: TokenCipher,
    val language: LanguageRepository,
    val storePaths: StorePaths,
    val brand: BrandConfig,
    val logger: (String) -> Unit,
)
