plugins {
    id("gasstation.android.library.compose")
    id("gasstation.android.hilt")
}

android {
    namespace = "com.gasstation.feature.settings"
}

dependencies {
    implementation(project(":domain:settings"))
    implementation(project(":domain:station"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
