plugins {
    id("gasstation.android.library")
    id("gasstation.android.hilt")
}

android {
    namespace = "com.gasstation.data.settings"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))
    implementation(project(":domain:settings"))
    testImplementation(libs.junit)
}
