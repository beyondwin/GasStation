plugins {
    id("gasstation.android.library")
    id("gasstation.android.hilt")
    id("gasstation.android.room")
}

android {
    namespace = "com.gasstation.core.database"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
