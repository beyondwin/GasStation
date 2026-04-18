plugins {
    id("gasstation.jvm.library")
    application
}

application {
    mainClass.set("com.gasstation.tools.demoseed.DemoSeedGeneratorMainKt")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":domain:station"))
    implementation(libs.converter.gson)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

val outputFile = rootProject.layout.projectDirectory.file("app/src/demo/assets/demo-station-seed.json")

tasks.register<JavaExec>("generateDemoSeed") {
    group = "demo seed"
    description = "Fetches the approved Gangnam demo matrix and writes the demo JSON asset."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args(outputFile.asFile.absolutePath)
    systemProperty("opinet.apikey", providers.gradleProperty("opinet.apikey").orNull ?: "")
}
