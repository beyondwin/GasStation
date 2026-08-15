package com.gasstation.buildlogic.testing

import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

enum class AndroidLintFixtureKind(val pluginId: String) {
    APPLICATION("gasstation.android.application.compose"),
    LIBRARY("gasstation.android.library"),
}

data class LintIssue(
    val id: String,
    val severity: String,
    val file: String,
    val line: Int?,
    val message: String,
)

fun GradlePluginTestProject.writeAndroidLintFixture(
    kind: AndroidLintFixtureKind,
    mainSource: String,
    testSource: String? = null,
    resources: Map<String, String> = emptyMap(),
    lintBaseline: String? = null,
): GradlePluginTestProject {
    writeSettings()
    writeFile("local.properties", "sdk.dir=${androidSdkDirectory().toPropertiesValue()}")
    writeFile("gradle/libs.versions.toml", ANDROID_LINT_VERSION_CATALOG)
    writeBuildFile(
        """
        plugins {
            id("${kind.pluginId}")
        }

        android {
            namespace = "fixture"
            ${if (kind == AndroidLintFixtureKind.APPLICATION) "defaultConfig { applicationId = \"fixture.application\" }" else ""}
            ${if (lintBaseline != null) "lint { baseline = file(\"lint-baseline.xml\") }" else ""}
        }
        """.trimIndent(),
    )
    writeFile("src/main/AndroidManifest.xml", "<manifest />")
    writeFile("src/main/java/fixture/MainSource.java", mainSource)
    testSource?.let { writeFile("src/test/java/fixture/TestOnlyNewApi.java", it) }
    resources.forEach { (path, content) -> writeFile(path, content) }
    lintBaseline?.let { writeFile("lint-baseline.xml", it) }
    return this
}

private fun androidSdkDirectory(): String {
    val environmentSdk =
        sequenceOf("ANDROID_HOME", "ANDROID_SDK_ROOT")
            .mapNotNull(System::getenv)
            .firstOrNull(String::isNotBlank)
    if (environmentSdk != null) return File(environmentSdk).canonicalPath

    val localProperties =
        generateSequence(File(System.getProperty("user.dir")).canonicalFile, File::getParentFile)
            .map { directory -> directory.resolve("local.properties") }
            .firstOrNull(File::isFile)
    require(localProperties != null) {
        "Android lint fixture requires ANDROID_HOME, ANDROID_SDK_ROOT, or repository local.properties"
    }
    val properties = Properties().apply { localProperties.inputStream().use(::load) }
    return File(properties.getProperty("sdk.dir").orEmpty()).canonicalPath.also { sdk ->
        require(File(sdk).isDirectory) { "Android SDK directory is missing: $sdk" }
    }
}

private fun String.toPropertiesValue(): String = replace("\\", "\\\\").replace(":", "\\:")

fun File.readLintIssues(): List<LintIssue> {
    require(isFile) { "Lint XML report missing: $this" }
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }
    val document = factory.newDocumentBuilder().parse(this)
    return (0 until document.getElementsByTagName("issue").length).map { index ->
        val issue = document.getElementsByTagName("issue").item(index)
        val attributes = issue.attributes
        val locations = issue.childNodes
        val location =
            (0 until locations.length)
                .map(locations::item)
                .firstOrNull { it.nodeName == "location" }
                ?: throw AssertionError("Lint issue has no location: ${attributes.getNamedItem("id")?.nodeValue}")
        val locationAttributes = location.attributes
        LintIssue(
            id = attributes.getNamedItem("id")?.nodeValue.orEmpty(),
            severity = attributes.getNamedItem("severity")?.nodeValue.orEmpty(),
            file = locationAttributes.getNamedItem("file")?.nodeValue.orEmpty(),
            line = locationAttributes.getNamedItem("line")?.nodeValue?.toIntOrNull(),
            message = attributes.getNamedItem("message")?.nodeValue.orEmpty(),
        )
    }
}

private val ANDROID_LINT_VERSION_CATALOG =
    """
    [versions]
    compileSdk = "37"
    minSdk = "24"
    targetSdk = "36"
    desugarJdkLibs = "2.1.5"
    coreKtx = "1.19.0"
    lifecycle = "2.11.0"
    activityCompose = "1.13.0"
    composeBom = "2026.06.01"
    coroutines = "1.11.0"
    junit4 = "4.13.2"
    androidxJunit = "1.3.0"
    espresso = "3.7.0"
    androidxTestCore = "1.7.0"
    robolectric = "4.16.1"

    [libraries]
    android-desugarJdkLibs = { module = "com.android.tools:desugar_jdk_libs", version.ref = "desugarJdkLibs" }
    androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
    androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
    androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
    androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
    androidx-ui = { module = "androidx.compose.ui:ui" }
    androidx-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
    androidx-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
    androidx-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
    androidx-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
    androidx-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
    androidx-material3 = { module = "androidx.compose.material3:material3" }
    junit = { module = "junit:junit", version.ref = "junit4" }
    androidx-junit = { module = "androidx.test.ext:junit", version.ref = "androidxJunit" }
    androidx-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
    kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
    androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }
    robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
    """.trimIndent()
