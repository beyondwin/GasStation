package com.gasstation.buildlogic.testing

import java.io.DataInputStream
import java.io.File

enum class KotlinConventionFixtureKind(
    val pluginId: String,
    val compileTask: String,
    val testTask: String,
) {
    JVM("gasstation.jvm.library", "compileKotlin", "test"),
    ANDROID_APPLICATION(
        "gasstation.android.application.compose",
        "compileDebugKotlin",
        "testDebugUnitTest",
    ),
    ANDROID_LIBRARY("gasstation.android.library", "compileDebugKotlin", "testDebugUnitTest"),
}

fun GradlePluginTestProject.writeKotlinConventionFixture(
    kind: KotlinConventionFixtureKind,
    projectPath: String = ":",
    warnedSource: Boolean = true,
): GradlePluginTestProject {
    require(projectPath == ":" || projectPath.matches(Regex("(?::[a-z][a-z0-9-]*)+"))) {
        "Fixture project path must be root or a lowercase absolute Gradle path: $projectPath"
    }
    if (kind == KotlinConventionFixtureKind.JVM) {
        writeJvmInfrastructure(projectPath)
    } else {
        require(projectPath == ":") { "Android convention probes use the root fixture project" }
        writeAndroidLintFixture(
            kind =
                if (kind == KotlinConventionFixtureKind.ANDROID_APPLICATION) {
                    AndroidLintFixtureKind.APPLICATION
                } else {
                    AndroidLintFixtureKind.LIBRARY
                },
            mainSource = JAVA_MARKER_SOURCE,
        )
    }

    val projectDirectory = projectPath.toProjectDirectory()
    writeFile(
        projectDirectory.resolveRelative("build.gradle.kts"),
        kotlinConventionBuildScript(kind),
    )
    writeFile(
        projectDirectory.resolveRelative("src/main/kotlin/fixture/WarningSource.kt"),
        if (warnedSource) WARNED_KOTLIN_SOURCE else WARNING_FREE_KOTLIN_SOURCE,
    )
    writeFile(
        projectDirectory.resolveRelative("src/test/kotlin/fixture/TestMarker.kt"),
        TEST_MARKER_SOURCE,
    )
    return this
}

fun GradlePluginTestProject.writeJvmKotlinConventionMultiProjectFixture(
    projectPaths: List<String>,
): GradlePluginTestProject {
    require(projectPaths.isNotEmpty()) { "At least one JVM convention project is required" }
    require(projectPaths.toSet().size == projectPaths.size) {
        "JVM convention project paths must be unique"
    }
    projectPaths.forEach { projectPath ->
        require(projectPath.matches(Regex("(?::[a-z][a-z0-9-]*)+"))) {
            "JVM convention project path must be lowercase and absolute: $projectPath"
        }
    }

    writeSettings(
        buildString {
            append(DEFAULT_KOTLIN_FIXTURE_SETTINGS)
            projectPaths.forEach { projectPath -> append("\n\ninclude(\"$projectPath\")") }
        },
    )
    writeFile("gradle/libs.versions.toml", JVM_VERSION_CATALOG)
    writeBuildFile("")
    projectPaths.forEach { projectPath ->
        val projectDirectory = projectPath.toProjectDirectory()
        writeFile(
            projectDirectory.resolveRelative("build.gradle.kts"),
            kotlinConventionBuildScript(KotlinConventionFixtureKind.JVM),
        )
        writeFile(
            projectDirectory.resolveRelative("src/main/kotlin/fixture/WarningSource.kt"),
            WARNED_KOTLIN_SOURCE,
        )
        writeFile(
            projectDirectory.resolveRelative("src/test/kotlin/fixture/TestMarker.kt"),
            TEST_MARKER_SOURCE,
        )
    }
    return this
}

fun GradlePluginTestProject.compiledClassFile(
    kind: KotlinConventionFixtureKind,
    projectPath: String = ":",
): File {
    val projectDirectory = projectPath.toProjectDirectory()
    val classesRoot =
        when (kind) {
            KotlinConventionFixtureKind.JVM -> "build/classes/kotlin/main"
            KotlinConventionFixtureKind.ANDROID_APPLICATION,
            KotlinConventionFixtureKind.ANDROID_LIBRARY,
            -> "build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
        }
    return projectDir.resolve(projectDirectory.resolveRelative("$classesRoot/fixture/WarningSourceKt.class"))
}

fun File.readJvmClassMajorVersion(): Int {
    require(isFile) { "Compiled class is missing: $this" }
    return DataInputStream(inputStream().buffered()).use { input ->
        require(input.readInt() == JAVA_CLASS_MAGIC) { "Not a JVM class file: $this" }
        input.readUnsignedShort()
        input.readUnsignedShort()
    }
}

private fun GradlePluginTestProject.writeJvmInfrastructure(projectPath: String) {
    writeSettings(
        buildString {
            append(DEFAULT_KOTLIN_FIXTURE_SETTINGS)
            if (projectPath != ":") append("\n\ninclude(\"$projectPath\")")
        },
    )
    writeFile("gradle/libs.versions.toml", JVM_VERSION_CATALOG)
    if (projectPath != ":") writeBuildFile("")
}

private fun kotlinConventionBuildScript(kind: KotlinConventionFixtureKind): String =
    """
    import org.gradle.api.DefaultTask
    import org.gradle.api.provider.Property
    import org.gradle.api.provider.ListProperty
    import org.gradle.api.tasks.Input
    import org.gradle.api.tasks.TaskAction
    import org.gradle.api.tasks.testing.Test
    import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

    abstract class ConventionProbe : DefaultTask() {
        @get:Input
        abstract val jvmTarget: Property<String>

        @get:Input
        abstract val warningsAsErrors: Property<Boolean>

        @get:Input
        abstract val testTimeoutMinutes: Property<Long>

        @TaskAction
        fun probe() {
            println("CONVENTION_JVM_TARGET=" + jvmTarget.get())
            println("CONVENTION_WARNINGS_AS_ERRORS=" + warningsAsErrors.get())
            println("CONVENTION_TEST_TIMEOUT_MINUTES=" + testTimeoutMinutes.get())
        }
    }

    abstract class ExplicitApiProbe : DefaultTask() {
        @get:Input
        abstract val compilerArguments: ListProperty<String>

        @TaskAction
        fun probe() {
            println("EXPLICIT_API_ARGUMENTS=" + compilerArguments.get().filter { it.startsWith("-Xexplicit-api=") })
        }
    }

    plugins {
        id("${kind.pluginId}")
    }

    ${
        if (kind == KotlinConventionFixtureKind.ANDROID_APPLICATION) {
            "android { namespace = \"fixture.application\"; defaultConfig { applicationId = \"fixture.application\" } }"
        } else if (kind == KotlinConventionFixtureKind.ANDROID_LIBRARY) {
            "android { namespace = \"fixture.library\" }"
        } else {
            ""
        }
    }

    val conventionProbe = tasks.register<ConventionProbe>("conventionProbe")
    val explicitApiProbe = tasks.register<ExplicitApiProbe>("explicitApiProbe")
    afterEvaluate {
        val conventionCompile = tasks.named<KotlinCompile>("${kind.compileTask}")
        val conventionTest = tasks.named<Test>("${kind.testTask}")
        conventionProbe.configure {
            dependsOn(conventionCompile)
            jvmTarget.set(conventionCompile.flatMap { it.compilerOptions.jvmTarget }.map { it.target })
            warningsAsErrors.set(conventionCompile.flatMap { it.compilerOptions.allWarningsAsErrors })
            testTimeoutMinutes.set(conventionTest.flatMap { it.timeout }.map { it.toMinutes() })
        }
        explicitApiProbe.configure {
            compilerArguments.set(conventionCompile.flatMap { it.compilerOptions.freeCompilerArgs })
        }
    }
    """.trimIndent()

private fun String.toProjectDirectory(): String = removePrefix(":").replace(':', '/')

private fun String.resolveRelative(relative: String): String =
    if (isEmpty()) relative else "$this/$relative"

private val DEFAULT_KOTLIN_FIXTURE_SETTINGS =
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

    rootProject.name = "gasstation-kotlin-convention-fixture"
    """.trimIndent()

private val JVM_VERSION_CATALOG =
    """
    [versions]
    kotlin = "2.4.10"

    [libraries]
    kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
    """.trimIndent()

private val JAVA_MARKER_SOURCE =
    """
    package fixture;

    public final class MainSource {}
    """.trimIndent()

private val WARNED_KOTLIN_SOURCE =
    """
    package fixture

    fun uncheckedStrings(value: Any): List<String> = value as List<String>
    """.trimIndent()

private val WARNING_FREE_KOTLIN_SOURCE =
    """
    package fixture

    fun checkedStrings(value: List<String>): List<String> = value
    """.trimIndent()

private val TEST_MARKER_SOURCE =
    """
    package fixture

    class TestMarker
    """.trimIndent()

private const val JAVA_CLASS_MAGIC = -889275714
