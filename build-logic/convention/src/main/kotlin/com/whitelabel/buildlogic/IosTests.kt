package com.whitelabel.buildlogic

internal const val SKIP_IOS_TESTS_PROPERTY = "whitelabel.skipIosTests"

/**
 * Whether the KMP modules link and run their iOS simulator tests. `scripts/check.sh` turns them
 * off (exactly `true`, with a warning) on a Mac without Xcode, which can compile for iOS but not
 * link. iOS compilation always runs.
 */
internal fun iosTestsEnabled(properties: Map<String, Any?>): Boolean = properties[SKIP_IOS_TESTS_PROPERTY] != "true"
