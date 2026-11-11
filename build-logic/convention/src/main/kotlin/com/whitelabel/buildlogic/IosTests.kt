package com.whitelabel.buildlogic

import org.gradle.api.Project

internal const val SKIP_IOS_TESTS_PROPERTY = "whitelabel.skipIosTests"

/**
 * Whether the KMP modules link and run their iOS simulator tests. A Mac without Xcode (only the
 * Command Line Tools) can compile for iOS but not link, so they are skipped there, with a warning;
 * `-Pwhitelabel.skipIosTests=true` (exactly) skips them anywhere. iOS compilation always runs.
 * Off a Mac, Kotlin disables the iOS targets itself.
 */
internal fun iosTestsEnabled(skipProperty: String?, isMac: Boolean, hasIosSdk: Boolean): Boolean =
    skipProperty != "true" && (!isMac || hasIosSdk)

/** Whether `xcrun` finds the iOS simulator SDK, i.e. a full Xcode is selected. */
internal fun Project.hasIosSimulatorSdk(): Boolean = providers.exec {
    commandLine("xcrun", "--sdk", "iphonesimulator", "--show-sdk-path")
    isIgnoreExitValue = true
}.result.get().exitValue == 0
