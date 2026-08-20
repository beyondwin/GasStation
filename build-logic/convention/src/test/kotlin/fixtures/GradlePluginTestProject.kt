package com.gasstation.buildlogic.testing

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.gradle.testkit.runner.GradleRunner

class GradlePluginTestProject private constructor(
    val projectDir: File,
    val testKitDir: File,
    val gradleUserHomeDir: File,
) {
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
                "--dependency-verification=strict",
                "-Dorg.gradle.java.installations.auto-detect=false",
                "-Dorg.gradle.java.installations.auto-download=false",
                "-Dorg.gradle.java.installations.paths=${compileJavaHome.absolutePath},${runtimeJavaHome.absolutePath}",
            )
        return createRunner(arguments, deterministicArguments)
    }

    fun configurationCacheRunner(vararg arguments: String): GradleRunner {
        arguments.forEach(::requireNonConflictingArgument)
        val deterministicArguments =
            listOf(
                "--configuration-cache",
                "--configuration-cache-problems=fail",
                "--no-build-cache",
                "--warning-mode=fail",
                "--stacktrace",
                "--gradle-user-home=${gradleUserHomeDir.absolutePath}",
                "--dependency-verification=strict",
                "-Dorg.gradle.java.installations.auto-detect=false",
                "-Dorg.gradle.java.installations.auto-download=false",
                "-Dorg.gradle.java.installations.paths=${compileJavaHome.absolutePath},${runtimeJavaHome.absolutePath}",
            )
        return createRunner(arguments, deterministicArguments)
    }

    private fun createRunner(
        arguments: Array<out String>,
        deterministicArguments: List<String>,
    ): GradleRunner {
        copyVerificationMetadata()
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withTestKitDir(testKitDir)
            .withGradleVersion(EXACT_GRADLE_VERSION)
            .withPluginClasspath()
            .withEnvironment(sanitizedEnvironment())
            .withArguments(arguments.toList() + deterministicArguments)
    }

    fun adversarialRunner(gradleUserHome: File, vararg arguments: String): GradleRunner {
        arguments.forEach(::requireNonConflictingArgument)
        copyVerificationMetadata()
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withTestKitDir(testKitDir)
            .withGradleVersion(EXACT_GRADLE_VERSION)
            .withPluginClasspath()
            .withEnvironment(sanitizedEnvironment())
            .withArguments(
                arguments.toList() +
                    listOf(
                        "--no-configuration-cache",
                        "--no-build-cache",
                        "--warning-mode=fail",
                        "--stacktrace",
                        "--gradle-user-home=${gradleUserHome.canonicalPath}",
                        "--dependency-verification=strict",
                        "-Dorg.gradle.java.installations.auto-detect=false",
                        "-Dorg.gradle.java.installations.auto-download=false",
                        "-Dorg.gradle.java.installations.paths=${compileJavaHome.absolutePath},${runtimeJavaHome.absolutePath}",
                    ),
            )
    }

    private val compileJavaHome: File
        get() = resolveJavaHome("JAVA_HOME_17_X64", 17)

    private val runtimeJavaHome: File
        get() = resolveJavaHome("JAVA_HOME_21_X64", 21)

    private fun resolveJavaHome(environmentName: String, major: Int): File {
        System.getenv(environmentName)?.takeIf(String::isNotBlank)?.let { return File(it).canonicalFile }
        val candidates =
            sequenceOf(
                File(System.getProperty("java.home")),
                File("/Library/Java/JavaVirtualMachines"),
                File(System.getProperty("user.home"), ".gradle/jdks"),
            ).flatMap { root ->
                if (!root.exists()) emptySequence() else root.walkTopDown().maxDepth(5)
            }.filter { it.isDirectory && it.resolve("bin/java").isFile && it.resolve("release").isFile }
        return candidates.firstOrNull { home ->
            Regex("(?m)^JAVA_VERSION=\"$major(?:[.\"].*)?\"")
                .containsMatchIn(home.resolve("release").readText())
        }?.canonicalFile ?: error("TestKit requires a Java $major home or $environmentName")
    }

    private fun sanitizedEnvironment(): Map<String, String> {
        val allowed = mutableMapOf(
            "HOME" to projectDir.parentFile.resolve("home").also(File::mkdirs).canonicalPath,
            "JAVA_HOME" to runtimeJavaHome.canonicalPath,
            "JAVA_HOME_17_X64" to compileJavaHome.canonicalPath,
            "JAVA_HOME_21_X64" to runtimeJavaHome.canonicalPath,
            "LANG" to "C.UTF-8",
            "LC_ALL" to "C.UTF-8",
            "PATH" to runtimeJavaHome.resolve("bin").canonicalPath + File.pathSeparator + "/usr/bin:/bin:/usr/sbin:/sbin",
            "TZ" to "UTC",
        )
        listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").forEach { name ->
            System.getenv(name)?.takeIf(String::isNotBlank)?.let { allowed[name] = it }
        }
        return allowed.toMap()
    }

    private fun copyVerificationMetadata() {
        val source = locateRepositoryRoot().resolve("gradle/verification-metadata.xml")
        require(source.isFile && !Files.isSymbolicLink(source.toPath())) {
            "Reviewed root dependency-verification metadata is missing: $source"
        }
        val destination = projectDir.resolve("gradle/verification-metadata.xml")
        destination.parentFile.mkdirs()
        Files.copy(source.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        require(source.sha256() == destination.sha256()) {
            "Fixture dependency-verification metadata copy hash differs from root"
        }
    }

    private fun locateRepositoryRoot(): File =
        generateSequence(File(System.getProperty("user.dir")).canonicalFile, File::getParentFile)
            .firstOrNull { it.resolve("gradle/wrapper/gradle-wrapper.properties").isFile }
            ?: error("Unable to locate repository root for TestKit metadata")

    private fun File.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(readBytes()).joinToString("") { "%02x".format(it) }

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
                argument == "--configuration-cache-problems" ||
                argument.startsWith("--configuration-cache-problems=") ||
                argument == "--build-cache" ||
                argument.startsWith("--build-cache=") ||
                argument == "--no-build-cache" ||
                argument.startsWith("--no-build-cache=") ||
                argument == "--warning-mode" ||
                argument.startsWith("--warning-mode=") ||
                argument == "-I" ||
                argument.startsWith("-I") ||
                argument == "--init-script" ||
                argument.startsWith("--init-script=") ||
                argument == "--dependency-verification" ||
                argument.startsWith("--dependency-verification=") ||
                argument == "--write-verification-metadata" ||
                argument.startsWith("--write-verification-metadata=") ||
                argument.startsWith("-Dorg.gradle.java.installations.") ||
                argument.contains("org.gradle.dependency.verification")
        require(!conflicts) { "Runner argument conflicts with harness policy: $argument" }
    }

    companion object {
        fun create(
            root: File,
            sharedGradleUserHomeDir: File? = null,
        ): GradlePluginTestProject {
            val canonicalRoot = root.canonicalFile
            require(canonicalRoot.isDirectory) { "Fixture root must be an existing directory: $root" }

            val projectDir = canonicalRoot.resolve(PROJECT_DIRECTORY)
            val testKitDir = canonicalRoot.resolve(TEST_KIT_DIRECTORY)
            listOf(projectDir, testKitDir).forEach { directory ->
                require(directory.mkdir()) {
                    "Fixture directory must be newly created and empty: $directory"
                }
            }
            val gradleUserHomeDir =
                sharedGradleUserHomeDir?.canonicalFile
                    ?: canonicalRoot.resolve(GRADLE_USER_HOME_DIRECTORY).also { directory ->
                        require(directory.mkdir()) {
                            "Fixture directory must be newly created and empty: $directory"
                        }
                    }
            require(gradleUserHomeDir.isDirectory) {
                "Shared Gradle user home must be an existing directory: $gradleUserHomeDir"
            }
            return GradlePluginTestProject(projectDir, testKitDir, gradleUserHomeDir)
        }

        private const val PROJECT_DIRECTORY = "project"
        private const val TEST_KIT_DIRECTORY = "test-kit"
        private const val GRADLE_USER_HOME_DIRECTORY = "gradle-user-home"
        private const val EXACT_GRADLE_VERSION = "9.6.1"

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
