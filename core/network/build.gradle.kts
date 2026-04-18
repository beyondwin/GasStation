plugins {
    id("gasstation.jvm.library")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":domain:station"))
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    implementation(libs.kotlinx.coroutines.core)
    implementation("com.google.dagger:hilt-core:2.59.2")
    implementation("org.locationtech.proj4j:proj4j:1.4.1")
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    ksp("com.google.dagger:hilt-compiler:2.59.2")
}
