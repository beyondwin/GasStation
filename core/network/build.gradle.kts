plugins {
    id("gasstation.jvm.library")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain:station"))
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.javax.inject)
    implementation("org.locationtech.proj4j:proj4j:1.4.1")
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
}
