plugins {
    id("gasstation.jvm.library")
    alias(libs.plugins.pitest)
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(libs.app.cash.turbine)
}

pitest {
    targetClasses.set(setOf("com.gasstation.domain.station.*"))
    targetTests.set(setOf("com.gasstation.domain.station.*"))
    threads.set(2)
    outputFormats.set(setOf("HTML", "XML"))
    timestampedReports.set(false)
    // report-only: mutationThreshold 게이트를 두지 않는다.
}
