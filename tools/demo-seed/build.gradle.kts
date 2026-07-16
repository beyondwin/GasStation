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

tasks.withType<Test>().configureEach {
    inputs.file(outputFile)
    systemProperty("demo.seed.asset.path", outputFile.asFile.absolutePath)
}

tasks.register<JavaExec>("generateDemoSeed") {
    group = "demo seed"
    description = "Fetches the approved Gangnam demo matrix and writes the demo JSON asset."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args(outputFile.asFile.absolutePath)
    systemProperty("opinet.apikey", providers.gradleProperty("opinet.apikey").orNull ?: "")
}

tasks.register<JavaExec>("verifyDemoSeedAsset") {
    group = "verification"
    description = "Verifies the committed demo seed without network access or credentials."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.gasstation.tools.demoseed.DemoSeedAssetVerifierMainKt")
    args(outputFile.asFile.absolutePath)
    inputs.file(outputFile)
}
