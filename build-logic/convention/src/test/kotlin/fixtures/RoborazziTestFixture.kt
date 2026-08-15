package com.gasstation.buildlogic.testing

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

fun GradlePluginTestProject.writeRoborazziFixture(
    projectPaths: List<String> = listOf(":"),
): GradlePluginTestProject {
    require(projectPaths.isNotEmpty()) { "At least one Roborazzi fixture project is required" }
    require(projectPaths.toSet().size == projectPaths.size) { "Roborazzi project paths must be unique" }
    projectPaths.forEach { path ->
        require(path == ":" || path.matches(Regex("(?::[a-z][a-z0-9-]*)+"))) {
            "Roborazzi fixture path must be root or a lowercase absolute Gradle path: $path"
        }
    }
    require(projectPaths.size == 1 || ":" !in projectPaths) {
        "The root fixture cannot also own Roborazzi subprojects"
    }

    writeAndroidLintFixture(
        kind = AndroidLintFixtureKind.LIBRARY,
        mainSource = JAVA_MARKER_SOURCE,
    )
    val catalogFile = projectDir.resolve("gradle/libs.versions.toml")
    val augmentedCatalog =
        catalogFile.readText()
            .replace(
                "\n[libraries]\n",
                "\nroborazzi = \"1.69.0\"\n\n[libraries]\n",
            )
            .trimEnd() +
            """

            roborazzi-core = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
            roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
            roborazzi-junit-rule = { module = "io.github.takahirom.roborazzi:roborazzi-junit-rule", version.ref = "roborazzi" }
            """.trimIndent()
    writeFile("gradle/libs.versions.toml", augmentedCatalog)

    writeSettings(
        buildString {
            append(ROBORAZZI_SETTINGS)
            projectPaths.filterNot { it == ":" }.forEach { append("\ninclude(\"$it\")") }
        },
    )
    if (":" !in projectPaths) writeBuildFile("")

    projectPaths.forEachIndexed { index, projectPath ->
        val directory = projectPath.toProjectDirectory()
        val packageName = "fixture.screen$index"
        val roborazziOutputDirectory =
            projectDir.resolve(directory.resolveRelative("build/outputs/roborazzi"))
        require(roborazziOutputDirectory.mkdirs()) {
            "Unable to create stable Roborazzi output directory: $roborazziOutputDirectory"
        }
        writeFile(
            directory.resolveRelative("build.gradle.kts"),
            roborazziBuildScript(packageName),
        )
        writeFile(
            directory.resolveRelative("src/main/AndroidManifest.xml"),
            "<manifest />",
        )
        writeFile(
            directory.resolveRelative("src/test/java/${packageName.replace('.', '/')}/NormalTest.java"),
            junitTestSource(packageName, "NormalTest", "normalTest"),
        )
        writeFile(
            directory.resolveRelative(
                "src/test/java/${packageName.replace('.', '/')}/RoborazziScreenshotTest.java",
            ),
            junitTestSource(packageName, "RoborazziScreenshotTest", "screenshotTest"),
        )
    }
    return this
}

fun GradlePluginTestProject.readExecutedTestClasses(projectPath: String = ":"): Set<String> {
    val directory = projectPath.toProjectDirectory()
    val results = projectDir.resolve(directory.resolveRelative("build/test-results/testDebugUnitTest"))
    require(results.isDirectory) { "JUnit XML directory is missing: $results" }
    val xmlFiles = results.listFiles { file -> file.isFile && file.extension == "xml" }.orEmpty()
    require(xmlFiles.isNotEmpty()) { "JUnit XML files are missing: $results" }
    val factory = secureDocumentBuilderFactory()
    return xmlFiles.flatMap { report ->
        val document = factory.newDocumentBuilder().parse(report)
        val cases = document.getElementsByTagName("testcase")
        (0 until cases.length).map { index ->
            cases.item(index).attributes.getNamedItem("classname")?.nodeValue
                ?: throw AssertionError("JUnit testcase has no classname: $report")
        }
    }.toSet()
}

fun GradlePluginTestProject.snapshotDirectory(projectPath: String = ":"): File =
    projectDir.resolve(
        projectPath.toProjectDirectory().resolveRelative("src/test/snapshots"),
    )

private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }

private fun roborazziBuildScript(namespace: String): String =
    """
    plugins {
        id("gasstation.android.library")
        id("gasstation.roborazzi")
    }

    android {
        namespace = "$namespace"
    }

    tasks.register("notRoborazziVerification") {
        dependsOn("testDebugUnitTest")
    }
    """.trimIndent()

private fun junitTestSource(
    packageName: String,
    className: String,
    methodName: String,
): String =
    """
    package $packageName;

    import org.junit.Test;

    public final class $className {
        @Test
        public void $methodName() {}
    }
    """.trimIndent()

private fun String.toProjectDirectory(): String = removePrefix(":").replace(':', '/')

private fun String.resolveRelative(relative: String): String =
    if (isEmpty()) relative else "$this/$relative"

private val ROBORAZZI_SETTINGS =
    """
    pluginManagement {
        repositories {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }

    dependencyResolutionManagement {
        repositories {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }

    rootProject.name = "gasstation-roborazzi-convention-fixture"
    """.trimIndent()

private val JAVA_MARKER_SOURCE =
    """
    package fixture;

    public final class MainSource {}
    """.trimIndent()
