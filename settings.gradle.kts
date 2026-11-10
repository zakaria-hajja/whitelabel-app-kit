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

include(":apps:globex")
include(":apps:globex:notes")
include(":apps:showcase")
include(":core:branding")
include(":core:common")
include(":core:network")
include(":core:designsystem")
include(":core:datastore")
include(":core:shell")
include(":core:ui")
include(":core:analytics")
include(":core:domain")
include(":core:data")

include(":feature:authentication")
