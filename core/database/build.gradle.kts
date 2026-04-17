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
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
}
