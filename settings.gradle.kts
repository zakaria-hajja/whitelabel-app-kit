pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "android-starter-kit"

// Shared modules: listed once, in gradle/foundation.settings.gradle.kts (app repos apply it too).
extra["foundationDir"] = settingsDir
apply(from = "gradle/foundation.settings.gradle.kts")

include(":apps:globex")
include(":apps:globex:notes")
include(":apps:showcase")
