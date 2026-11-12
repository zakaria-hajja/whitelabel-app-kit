// The foundation's shared modules, in one place. Applied by the foundation's own settings and by
// every app repo that has the foundation as a submodule:
//
//     extra["foundationDir"] = settingsDir.resolve("foundation")   // default; the foundation root
//     apply(from = "foundation/gradle/foundation.settings.gradle.kts")
//
// Each module keeps its usual path (":core:shell"), so the modules' own project(":core:x")
// references resolve unchanged. Adding a shared module to the foundation is one line below.

val foundationDir: File = (settings.extra.properties["foundationDir"] as File?) ?: settingsDir.resolve("foundation")

val sharedModules = listOf(
    ":core:branding",
    ":core:common",
    ":core:presentation",
    ":core:network",
    ":core:designsystem",
    ":core:datastore",
    ":core:reminders",
    ":core:reminders:shared",
    ":core:shell",
    ":core:shell:shared",
    ":core:ios",
    ":core:ios:mock",
    ":core:ui",
    ":core:analytics",
    ":core:ads:shared",
    ":core:consent:shared",
    ":core:consent:google",
    ":core:firebase:shared",
    ":core:firebase:google",
    ":core:firebase:crashlytics",
    ":core:domain",
    ":core:data",
    ":feature:authentication",
    ":feature:authentication:shared",
    ":feature:settings",
    ":feature:settings:shared",
    ":feature:profile",
    ":feature:profile:shared",
    ":feature:home",
    ":feature:home:shared",
    ":feature:catalog",
    ":feature:catalog:shared",
    ":core:testing",
    ":core:mockapi",
    ":core:testing-android",
)

sharedModules.forEach { path ->
    include(path)
    project(path).projectDir = foundationDir.resolve(path.removePrefix(":").replace(':', '/'))
}

// An app repo shares the foundation's version catalog. The foundation's own build gets it from
// gradle/libs.versions.toml automatically, so declaring it there again would be an error.
if (foundationDir.canonicalFile != settingsDir.canonicalFile) {
    dependencyResolutionManagement {
        versionCatalogs {
            create("libs") { from(files(foundationDir.resolve("gradle/libs.versions.toml"))) }
        }
    }
}
