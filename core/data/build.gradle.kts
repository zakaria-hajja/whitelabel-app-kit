plugins {
    id("whitelabel.kmp.library")
    id("whitelabel.koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            implementation(project(":core:network"))
            implementation(project(":core:datastore"))
            implementation(project(":core:branding"))
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.appcompat)
            implementation(libs.koin.android)
        }
        commonTest.dependencies { implementation(libs.ktor.client.mock) }
        jvmTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.ktor.client.mock)
        }
        getByName("androidDeviceTest").dependencies { implementation(libs.ktor.client.mock) }
    }
}
