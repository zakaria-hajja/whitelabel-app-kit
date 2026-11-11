plugins {
    `kotlin-dsl`
}

group = "com.whitelabel.buildlogic"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
    compileOnly(libs.android.junit5.gradlePlugin)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertk)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "whitelabel.jvm.library"
            implementationClass = "com.whitelabel.buildlogic.JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "whitelabel.android.library"
            implementationClass = "com.whitelabel.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "whitelabel.android.library.compose"
            implementationClass = "com.whitelabel.buildlogic.AndroidLibraryComposeConventionPlugin"
        }
        register("androidApplication") {
            id = "whitelabel.android.application"
            implementationClass = "com.whitelabel.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("kmpLibrary") {
            id = "whitelabel.kmp.library"
            implementationClass = "com.whitelabel.buildlogic.KmpLibraryConventionPlugin"
        }
        register("kmpResources") {
            id = "whitelabel.kmp.resources"
            implementationClass = "com.whitelabel.buildlogic.KmpResourcesConventionPlugin"
        }
        register("iosApp") {
            id = "whitelabel.ios.app"
            implementationClass = "com.whitelabel.buildlogic.IosAppConventionPlugin"
        }
        register("koin") {
            id = "whitelabel.koin"
            implementationClass = "com.whitelabel.buildlogic.KoinConventionPlugin"
        }
    }
}
