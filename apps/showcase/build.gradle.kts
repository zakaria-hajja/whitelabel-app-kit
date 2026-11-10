plugins {
    id("whitelabel.android.application")
}

android {
    namespace = "com.whitelabel.apps.showcase"
}

dependencies {
    implementation(project(":core:shell"))
}
