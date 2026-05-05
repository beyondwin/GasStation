plugins {
    id("gasstation.android.library")
    id("gasstation.android.hilt")
}

android {
    namespace = "com.gasstation.core.datastore"
}

dependencies {
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.core)
}
