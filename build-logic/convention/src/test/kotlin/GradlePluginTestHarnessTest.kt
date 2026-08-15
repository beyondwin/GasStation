package com.gasstation.buildlogic

import com.gasstation.buildlogic.testing.GradlePluginTestProject
import com.gasstation.buildlogic.testing.assertOutputContainsExactlyOnce
import com.gasstation.buildlogic.testing.assertOutputDoesNotContain
import com.gasstation.buildlogic.testing.assertTaskOutcome
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GradlePluginTestHarnessTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun successfulPluginBuildReportsIsolatedEnvironmentAndExactSentinels() {
        val project = newProject("success").writeSettings().writeBuildFile(successBuildScript())
            .writeFile("src/main/kotlin/Fixture.kt", "package fixture\n\nclass Fixture")

        val result = project.runner("spotlessCheck", "harnessEnvironment").build()

        result.assertTaskOutcome(":spotlessCheck", TaskOutcome.SUCCESS)
        result.assertTaskOutcome(":harnessEnvironment", TaskOutcome.SUCCESS)
        result.assertOutputContainsExactlyOnce("HARNESS_GRADLE_VERSION=9.6.1")
        result.assertOutputContainsExactlyOnce(
            "HARNESS_GRADLE_USER_HOME=${project.gradleUserHomeDir.canonicalPath}",
        )
        result.assertOutputContainsExactlyOnce("HARNESS_SUCCESS")
        result.assertOutputDoesNotContain("HARNESS_FABRICATED_FAILURE")

        val javaVersionMatches =
            Regex("""(?m)^HARNESS_JAVA_VERSION=(\d+)$""").findAll(result.output).toList()
        assertEquals("exactly one Java-version sentinel", 1, javaVersionMatches.size)
        assertTrue(
            "fixture JVM must be compatible with Java 17",
            javaVersionMatches.single().groupValues[1].toInt() >= 17,
        )
        assertNotEquals(
            File(System.getProperty("user.home"), ".gradle").canonicalFile,
            project.gradleUserHomeDir.canonicalFile,
        )
        println("HARNESS_VERIFIED_GRADLE_VERSION=9.6.1")
        println("HARNESS_VERIFIED_JAVA_VERSION=${javaVersionMatches.single().groupValues[1]}")
        println("HARNESS_VERIFIED_GRADLE_USER_HOME=${project.gradleUserHomeDir.canonicalPath}")

        val missingTaskError =
            assertThrows(AssertionError::class.java) {
                result.assertTaskOutcome(":missingHarnessTask", TaskOutcome.SUCCESS)
            }
        assertTrue(missingTaskError.message.orEmpty().contains(":missingHarnessTask"))
        assertTrue(missingTaskError.message.orEmpty().contains(":spotlessCheck=SUCCESS"))

        val wrongOutcomeError =
            assertThrows(AssertionError::class.java) {
                result.assertTaskOutcome(":harnessEnvironment", TaskOutcome.FAILED)
            }
        assertTrue(wrongOutcomeError.message.orEmpty().contains(":harnessEnvironment"))
        assertTrue(wrongOutcomeError.message.orEmpty().contains("expected=FAILED"))
        assertTrue(wrongOutcomeError.message.orEmpty().contains("actual=SUCCESS"))
    }

    @Test
    fun intentionalFailureRequiresTheNamedFailedTaskAndUniqueSentinel() {
        val project = newProject("failure").writeSettings().writeBuildFile(failureBuildScript())

        val result = project.runner("intentionalHarnessFailure").buildAndFail()

        result.assertTaskOutcome(":intentionalHarnessFailure", TaskOutcome.FAILED)
        result.assertOutputContainsExactlyOnce(INTENTIONAL_FAILURE)
        result.assertOutputDoesNotContain("HARNESS_SUCCESS")
        assertThrows(UnexpectedBuildFailure::class.java) {
            project.runner("intentionalHarnessFailure").build()
        }
    }

    @Test
    fun outputAssertionsRejectMissingDuplicateAndBlankSentinels() {
        val project = newProject("sentinels").writeSettings().writeBuildFile(
            successBuildScript(
                extraOutput = "println(\"HARNESS_DUPLICATE\")\nprintln(\"HARNESS_DUPLICATE\")",
            ),
        )
        val result = project.runner("harnessEnvironment").build()

        assertThrows(AssertionError::class.java) {
            result.assertOutputContainsExactlyOnce("HARNESS_MISSING")
        }
        assertThrows(AssertionError::class.java) {
            result.assertOutputContainsExactlyOnce("HARNESS_DUPLICATE")
        }
        assertThrows(IllegalArgumentException::class.java) {
            result.assertOutputContainsExactlyOnce("  ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            result.assertOutputDoesNotContain("")
        }
    }

    @Test
    fun builderWritesNestedUtf8WithExactlyOneFinalNewline() {
        val project = newProject("utf8")

        val returned = project.writeFile("nested/source.txt", "가격\n\n")

        assertEquals(project, returned)
        assertArrayEquals(
            "가격\n".toByteArray(UTF_8),
            project.projectDir.resolve("nested/source.txt").readBytes(),
        )
    }

    @Test
    fun builderRejectsAbsoluteTraversalAndResolvedSymlinkEscapes() {
        val project = newProject("unsafe-paths")
        val outside = temporaryFolder.newFolder("outside")
        Files.createSymbolicLink(project.projectDir.toPath().resolve("escape-link"), outside.toPath())

        assertThrows(IllegalArgumentException::class.java) {
            project.writeFile(outside.resolve("absolute.txt").absolutePath, "unsafe")
        }
        assertThrows(IllegalArgumentException::class.java) {
            project.writeFile("../traversal.txt", "unsafe")
        }
        assertThrows(IllegalArgumentException::class.java) {
            project.writeFile("safe/../alias.txt", "unsafe")
        }
        assertThrows(IllegalArgumentException::class.java) {
            project.writeFile("escape-link/outside.txt", "unsafe")
        }
        assertFalse(outside.resolve("outside.txt").exists())
    }

    @Test
    fun runnerRejectsEveryIsolationAndPolicyOverrideForm() {
        val project = newProject("runner-options")
        val conflicts =
            listOf(
                arrayOf("-g", "/tmp/other-home"),
                arrayOf("-g/tmp/other-home"),
                arrayOf("--gradle-user-home", "/tmp/other-home"),
                arrayOf("--gradle-user-home=/tmp/other-home"),
                arrayOf("--configuration-cache"),
                arrayOf("--configuration-cache=true"),
                arrayOf("--no-configuration-cache"),
                arrayOf("--no-configuration-cache=true"),
                arrayOf("--build-cache"),
                arrayOf("--build-cache=true"),
                arrayOf("--no-build-cache"),
                arrayOf("--no-build-cache=true"),
                arrayOf("--warning-mode", "all"),
                arrayOf("--warning-mode=summary"),
                arrayOf("--warning-mode=fail"),
            )

        conflicts.forEach { arguments ->
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    project.runner(*arguments)
                }
            assertTrue(
                "diagnostic must identify conflicting option ${arguments.first()}",
                error.message.orEmpty().contains(arguments.first()),
            )
        }
        assertNotSame(project.runner("help"), project.runner("help"))
    }

    @Test
    fun independentFixturesCannotReuseDirectoriesOrStaleFiles() {
        val firstRoot = temporaryFolder.newFolder("first-root")
        val secondRoot = temporaryFolder.newFolder("second-root")
        val first = GradlePluginTestProject.create(firstRoot).writeFile("stale.txt", "stale")
        val second = GradlePluginTestProject.create(secondRoot)

        assertNotEquals(first.projectDir.canonicalFile, second.projectDir.canonicalFile)
        assertNotEquals(first.testKitDir.canonicalFile, second.testKitDir.canonicalFile)
        assertNotEquals(first.gradleUserHomeDir.canonicalFile, second.gradleUserHomeDir.canonicalFile)
        assertFalse(second.projectDir.resolve("stale.txt").exists())
        assertThrows(IllegalArgumentException::class.java) {
            GradlePluginTestProject.create(firstRoot)
        }
    }

    private fun newProject(name: String): GradlePluginTestProject =
        GradlePluginTestProject.create(temporaryFolder.newFolder("$name-root"))

    private fun successBuildScript(extraOutput: String = ""): String {
        val extraOutputSuffix =
            extraOutput.takeIf(String::isNotBlank)?.lineSequence()?.joinToString(
                prefix = "\n",
                separator = "\n",
            ) { "                $it" }.orEmpty()
        return """
        plugins {
            id("gasstation.spotless")
        }

        spotless {
            kotlin {
                clearSteps()
                trimTrailingWhitespace()
                endWithNewline()
            }
            kotlinGradle {
                clearSteps()
                trimTrailingWhitespace()
                endWithNewline()
            }
        }

        tasks.register("harnessEnvironment") {
            doLast {
                println("HARNESS_GRADLE_VERSION=${'$'}{gradle.gradleVersion}")
                println("HARNESS_JAVA_VERSION=${'$'}{JavaVersion.current().majorVersion}")
                println("HARNESS_GRADLE_USER_HOME=${'$'}{gradle.gradleUserHomeDir.canonicalPath}")
                println("HARNESS_SUCCESS")$extraOutputSuffix
            }
        }
        """.trimIndent()
    }

    private fun failureBuildScript(): String =
        """
        plugins {
            id("gasstation.spotless")
        }

        tasks.register("intentionalHarnessFailure") {
            doLast {
                println("$INTENTIONAL_FAILURE")
                throw GradleException("Intentional TestKit harness failure")
            }
        }
        """.trimIndent()

    private companion object {
        const val INTENTIONAL_FAILURE = "HARNESS_INTENTIONAL_FAILURE_4A"
    }
}
