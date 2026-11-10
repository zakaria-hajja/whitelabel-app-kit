plugins {
    id("whitelabel.android.application")
}

android {
    namespace = "com.whitelabel.apps.globex"
}

dependencies {
    implementation(project(":core:shell"))
    implementation(project(":apps:globex:notes"))
}
