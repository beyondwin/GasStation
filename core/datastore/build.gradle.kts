plugins {
    id("gasstation.android.library")
    id("gasstation.android.hilt")
}

android {
    namespace = "com.gasstation.core.datastore"
}

dependencies {
    implementation(project(":domain:settings"))
    implementation(project(":domain:station"))
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.core)
    testImplementation(libs.junit)
}
