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
    pitestVersion.set(libs.versions.pitestEngine)
    targetClasses.set(setOf("com.gasstation.domain.station.*"))
    targetTests.set(setOf("com.gasstation.domain.station.*"))
    threads.set(2)
    outputFormats.set(setOf("HTML", "XML"))
    timestampedReports.set(false)
    // floor 게이트: 현재 변이 점수 47%에서 하락을 막는다. 점수가 오르면 floor도 함께 올린다.
    mutationThreshold.set(40)
}
