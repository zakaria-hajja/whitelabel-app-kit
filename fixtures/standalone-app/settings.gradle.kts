// A minimal app in its own repo, the way Munch and later apps consume the foundation. Here the
// foundation is two directories up; in a real app repo it is the `foundation/` submodule.
// CI builds it so foundation changes that would break such apps fail here first.
pluginManagement {
    includeBuild("../../build-logic")
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

rootProject.name = "standalone-app-fixture"

extra["foundationDir"] = settingsDir.resolve("../..")
apply(from = "../../gradle/foundation.settings.gradle.kts")

include(":app")
