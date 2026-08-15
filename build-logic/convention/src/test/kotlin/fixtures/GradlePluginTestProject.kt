package com.gasstation.buildlogic.testing

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import org.gradle.testkit.runner.GradleRunner

class GradlePluginTestProject private constructor(
    val projectDir: File,
    val testKitDir: File,
) {
    val gradleUserHomeDir: File = projectDir.parentFile.resolve(GRADLE_USER_HOME_DIRECTORY)

    fun writeSettings(content: String = DEFAULT_SETTINGS): GradlePluginTestProject =
        writeFile("settings.gradle.kts", content)

    fun writeBuildFile(content: String): GradlePluginTestProject =
        writeFile("build.gradle.kts", content)

    fun writeFile(relativePath: String, content: String): GradlePluginTestProject = apply {
        require(relativePath.isNotBlank()) { "Fixture path must not be blank" }
        val requestedPath = Path.of(relativePath)
        require(!requestedPath.isAbsolute) { "Fixture path must be relative: $relativePath" }
        require(requestedPath.none { it.toString() == ".." }) {
            "Fixture path must not contain '..': $relativePath"
        }

        val canonicalProjectDir = projectDir.canonicalFile
        val target = canonicalProjectDir.resolve(relativePath).canonicalFile
        require(target != canonicalProjectDir && target.toPath().startsWith(canonicalProjectDir.toPath())) {
            "Fixture path resolves outside project directory: $relativePath"
        }
        require(target.parentFile.mkdirs() || target.parentFile.isDirectory) {
            "Unable to create fixture parent directory: ${target.parentFile}"
        }
        target.writeText(content.trimEnd('\r', '\n') + "\n", UTF_8)
    }

    fun runner(vararg arguments: String): GradleRunner {
        arguments.forEach(::requireNonConflictingArgument)
        val deterministicArguments =
            listOf(
                "--no-configuration-cache",
                "--no-build-cache",
                "--warning-mode=fail",
                "--stacktrace",
                "--gradle-user-home=${gradleUserHomeDir.absolutePath}",
            )
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withTestKitDir(testKitDir)
            .withPluginClasspath()
            .withArguments(arguments.toList() + deterministicArguments)
    }

    private fun requireNonConflictingArgument(argument: String) {
        val conflicts =
            argument == "-g" ||
                argument.startsWith("-g") ||
                argument == "--gradle-user-home" ||
                argument.startsWith("--gradle-user-home=") ||
                argument == "--configuration-cache" ||
                argument.startsWith("--configuration-cache=") ||
                argument == "--no-configuration-cache" ||
                argument.startsWith("--no-configuration-cache=") ||
                argument == "--build-cache" ||
                argument.startsWith("--build-cache=") ||
                argument == "--no-build-cache" ||
                argument.startsWith("--no-build-cache=") ||
                argument == "--warning-mode" ||
                argument.startsWith("--warning-mode=")
        require(!conflicts) { "Runner argument conflicts with harness policy: $argument" }
    }

    companion object {
        fun create(root: File): GradlePluginTestProject {
            val canonicalRoot = root.canonicalFile
            require(canonicalRoot.isDirectory) { "Fixture root must be an existing directory: $root" }

            val projectDir = canonicalRoot.resolve(PROJECT_DIRECTORY)
            val testKitDir = canonicalRoot.resolve(TEST_KIT_DIRECTORY)
            val gradleUserHomeDir = canonicalRoot.resolve(GRADLE_USER_HOME_DIRECTORY)
            listOf(projectDir, testKitDir, gradleUserHomeDir).forEach { directory ->
                require(directory.mkdir()) {
                    "Fixture directory must be newly created and empty: $directory"
                }
            }
            return GradlePluginTestProject(projectDir, testKitDir)
        }

        private const val PROJECT_DIRECTORY = "project"
        private const val TEST_KIT_DIRECTORY = "test-kit"
        private const val GRADLE_USER_HOME_DIRECTORY = "gradle-user-home"

        private val DEFAULT_SETTINGS =
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

            rootProject.name = "gasstation-convention-plugin-test-fixture"
            """.trimIndent()
    }
}
