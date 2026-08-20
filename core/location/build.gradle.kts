plugins {
    id("gasstation.android.library")
    id("gasstation.android.hilt")
}

android {
    namespace = "com.gasstation.core.location"
    defaultConfig {
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
    }
    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:observability"))
    implementation(project(":domain:location"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.location)
    testImplementation(libs.app.cash.turbine)
    testImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.junit)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)
}
