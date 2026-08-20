plugins {
    id("gasstation.jvm.mutation")
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(libs.kotlinx.coroutines.test)
}
