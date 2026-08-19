plugins {
    id("gasstation.jvm.library")
    alias(libs.plugins.pitest)
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(libs.kotlinx.coroutines.test)
}

pitest {
    pitestVersion.set(libs.versions.pitestEngine)
    targetClasses.set(setOf("com.gasstation.domain.location.*"))
    targetTests.set(setOf("com.gasstation.domain.location.*"))
    threads.set(2)
    outputFormats.set(setOf("HTML", "XML"))
    timestampedReports.set(false)
    // report-only: 베이스라인 캡처 단계. 게이트는 점수 안정화 후 domain:station처럼 별도 결정.
}
