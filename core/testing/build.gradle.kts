plugins {
    id("gasstation.android.library")
}

android {
    namespace = "com.gasstation.core.testing"
}

dependencies {
    api(libs.junit)
    api(libs.androidx.junit)
    api(libs.androidx.espresso.core)
}
