package com.gasstation.buildlogic

import com.gasstation.buildlogic.testing.GradlePluginTestProject
import com.gasstation.buildlogic.testing.assertConfigurationCacheReused
import com.gasstation.buildlogic.testing.assertConfigurationCacheStored
import com.gasstation.buildlogic.testing.assertTaskOutcome
import com.gasstation.buildlogic.testing.readExecutedTestClasses
import com.gasstation.buildlogic.testing.snapshotDirectory
import com.gasstation.buildlogic.testing.writeRoborazziFixture
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class RoborazziPropertySelectionTest : RoborazziConventionTestSupport() {
    @Test
    fun ordinaryUnitTestExcludesScreenshotClassForAbsentAndExactFalseProperty() {
        val project = newProject("ordinary").writeRoborazziFixture()

        listOf(emptyArray(), arrayOf("-Pgasstation.includeRoborazziInUnitTests=false"))
            .forEach { propertyArguments ->
                val result =
                    project.runner("testDebugUnitTest", "--rerun-tasks", *propertyArguments).build()

                result.assertTaskOutcome(":testDebugUnitTest", TaskOutcome.SUCCESS)
                assertTestClasses(project, includeScreenshot = false)
            }
    }

    @Test
    fun exactTrueIncludesScreenshotClassAndFalseTrueFalseNeverLeavesStaleXml() {
        val project = newProject("property-sequence").writeRoborazziFixture()
        val sequence =
            listOf(
                "false" to false,
                "true" to true,
                "false" to false,
            )

        sequence.forEach { (value, includeScreenshot) ->
            val result =
                project.runner(
                    "testDebugUnitTest",
                    "--rerun-tasks",
                    "-Pgasstation.includeRoborazziInUnitTests=$value",
                ).build()

            result.assertTaskOutcome(":testDebugUnitTest", TaskOutcome.SUCCESS)
            assertTestClasses(project, includeScreenshot = includeScreenshot)
        }
    }
}

internal class RoborazziPropertyValidationTest : RoborazziConventionTestSupport() {
    @Test
    fun invalidIncludePropertyUsesSharedStrictParserBeforeTheUnitTestCanSucceed() {
        val project = newProject("invalid-property").writeRoborazziFixture()
        val result =
            project.runner(
                "testDebugUnitTest",
                "--rerun-tasks",
                "-Pgasstation.includeRoborazziInUnitTests=TRUE",
            ).buildAndFail()

        assertTrue(
            result.output.contains(
                "gasstation.includeRoborazziInUnitTests must be exactly true or false",
            ),
        )
        assertFalse(
            result.tasks.any { task ->
                task.path == ":testDebugUnitTest" && task.outcome == TaskOutcome.SUCCESS
            },
        )
    }
}

internal class RoborazziLifecycleSelectionTest : RoborazziConventionTestSupport() {
    @Test
    fun unqualifiedAndQualifiedLifecycleRequestsAreProjectAware() {
        val allProjects = newProject("unqualified-multi")
            .writeRoborazziFixture(listOf(":first", ":second"))
        val unqualified =
            allProjects.runner("verifyRoborazziDebug", "--rerun-tasks").build()
        unqualified.assertTaskOutcome(":first:verifyRoborazziDebug", TaskOutcome.SUCCESS)
        unqualified.assertTaskOutcome(":second:verifyRoborazziDebug", TaskOutcome.SUCCESS)
        assertTestClasses(allProjects, ":first", includeScreenshot = true)
        assertTestClasses(allProjects, ":second", includeScreenshot = true)

        val qualified = newProject("qualified-multi")
            .writeRoborazziFixture(listOf(":first", ":second"))
        val qualifiedResult =
            qualified.runner(
                ":first:verifyRoborazziDebug",
                ":second:testDebugUnitTest",
                "--rerun-tasks",
            ).build()
        qualifiedResult.assertTaskOutcome(":first:verifyRoborazziDebug", TaskOutcome.SUCCESS)
        qualifiedResult.assertTaskOutcome(":second:testDebugUnitTest", TaskOutcome.SUCCESS)
        assertTestClasses(qualified, ":first", includeScreenshot = true)
        assertTestClasses(qualified, ":second", includeScreenshot = false)
    }

    @Test
    fun projectPathAndNearMatchTaskNamesDoNotSelectScreenshotTests() {
        val project =
            newProject("project-and-task-near-match")
                .writeRoborazziFixture(listOf(":roborazzi-screen", ":task-near-match"))
        val result =
            project.runner(
                ":roborazzi-screen:testDebugUnitTest",
                ":task-near-match:notRoborazziVerification",
                "--rerun-tasks",
            ).build()

        result.assertTaskOutcome(":roborazzi-screen:testDebugUnitTest", TaskOutcome.SUCCESS)
        result.assertTaskOutcome(":task-near-match:notRoborazziVerification", TaskOutcome.SUCCESS)
        result.assertTaskOutcome(":task-near-match:testDebugUnitTest", TaskOutcome.SUCCESS)
        assertTestClasses(project, ":roborazzi-screen", includeScreenshot = false)
        assertTestClasses(project, ":task-near-match", includeScreenshot = false)
    }
}

internal class RoborazziAggregateLifecycleTest : RoborazziConventionTestSupport() {
    @Test
    fun everyAggregateLifecycleFamilySelectsScreenshotTestsAndRunsStagingGuard() {
        val families =
            listOf(
                "recordRoborazzi",
                "verifyRoborazzi",
                "compareRoborazzi",
                "verifyAndRecordRoborazzi",
            )
        val fixturePaths =
            mapOf(
                "recordRoborazzi" to ":record",
                "verifyRoborazzi" to ":verify",
                "compareRoborazzi" to ":compare",
                "verifyAndRecordRoborazzi" to ":verify-record",
            )
        val project =
            newProject("aggregate-families")
                .writeRoborazziFixture(fixturePaths.values.toList())
        fixturePaths.values.forEach { projectPath ->
            writePng(project.snapshotDirectory(projectPath).resolve("staging.png"), MAGENTA_ARGB)
        }

        val requestedTasks =
            families.map { taskName ->
                "${fixturePaths.getValue(taskName)}:$taskName"
            }
        val result =
            project.runner(*requestedTasks.toTypedArray(), "--continue").buildAndFail()

        families.forEach { taskName ->
            val projectPath = fixturePaths.getValue(taskName)
            result.assertTaskOutcome("$projectPath:testDebugUnitTest", TaskOutcome.SUCCESS)
            result.assertTaskOutcome("$projectPath:${taskName}Debug", TaskOutcome.FAILED)
            assertTestClasses(project, projectPath, includeScreenshot = true)
        }
        assertTrue(result.output.contains("exact staging magenta pixel(s)"))
    }

    @Test
    fun stagingValidatorAcceptsEmptyAndCleanDirectoriesThroughLifecycleTask() {
        val project = newProject("clean-staging").writeRoborazziFixture()

        val empty = project.runner("verifyRoborazzi", "--rerun-tasks").build()
        empty.assertTaskOutcome(":verifyRoborazzi", TaskOutcome.SUCCESS)
        assertTestClasses(project, includeScreenshot = true)

        writePng(project.snapshotDirectory().resolve("clean.png"), 0xFF112233.toInt())
        val clean = project.runner("verifyRoborazzi", "--rerun-tasks").build()
        clean.assertTaskOutcome(":verifyRoborazzi", TaskOutcome.SUCCESS)
        assertTestClasses(project, includeScreenshot = true)
    }
}

internal class RoborazziConfigurationCacheTest : RoborazziConventionTestSupport() {
    @Test
    fun roborazziSelectionsStoreAndReuseAcrossPolicyChanges() {
        val project = newProject("configuration-cache").writeRoborazziFixture()

        assertCachePair(
            project = project,
            arguments = arrayOf("testDebugUnitTest", "--rerun-tasks"),
            expectedTaskPaths = setOf(":testDebugUnitTest"),
        ) {
            assertTestClasses(project, includeScreenshot = false)
        }
        assertCachePair(
            project = project,
            arguments = arrayOf(
                "testDebugUnitTest",
                "--rerun-tasks",
                "-Pgasstation.includeRoborazziInUnitTests=true",
            ),
            expectedTaskPaths = setOf(":testDebugUnitTest"),
        ) {
            assertTestClasses(project, includeScreenshot = true)
        }
        assertCachePair(
            project = project,
            arguments = arrayOf("verifyRoborazziDebug", "--rerun-tasks"),
            expectedTaskPaths = setOf(":verifyRoborazziDebug", ":testDebugUnitTest"),
        ) {
            assertTestClasses(project, includeScreenshot = true)
        }
        assertCachePair(
            project = project,
            arguments = arrayOf(":verifyRoborazziDebug", "--rerun-tasks"),
            expectedTaskPaths = setOf(":verifyRoborazziDebug", ":testDebugUnitTest"),
        ) {
            assertTestClasses(project, includeScreenshot = true)
        }
    }
}

internal abstract class RoborazziConventionTestSupport {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    protected val sharedGradleUserHome by lazy {
        temporaryFolder.newFolder("roborazzi-gradle-user-home")
    }

    protected fun assertCachePair(
        project: GradlePluginTestProject,
        arguments: Array<String>,
        expectedTaskPaths: Set<String>,
        assertEvidence: (BuildResult) -> Unit,
    ) {
        require(arguments.isNotEmpty()) { "Cache-pair arguments must request a task" }
        val requestedTaskPath = arguments.first().toAbsoluteTaskPath()
        val requiredTaskPaths =
            buildSet {
                add(requestedTaskPath)
                add(requestedTaskPath.owningUnitTestTaskPath())
            }
        require(expectedTaskPaths == requiredTaskPaths) {
            "Cache-pair task evidence must exactly match the requested lifecycle: " +
                "expected=$requiredTaskPaths actual=$expectedTaskPaths"
        }

        clearTestResults(project, expectedTaskPaths)
        val first = project.configurationCacheRunner(*arguments).build()
        first.assertConfigurationCacheStored()
        expectedTaskPaths.forEach { taskPath ->
            first.assertTaskOutcome(taskPath, TaskOutcome.SUCCESS)
        }
        assertEvidence(first)

        clearTestResults(project, expectedTaskPaths)
        val second = project.configurationCacheRunner(*arguments).build()
        second.assertConfigurationCacheReused()
        expectedTaskPaths.forEach { taskPath ->
            second.assertTaskOutcome(taskPath, TaskOutcome.SUCCESS)
        }
        assertEvidence(second)
    }

    protected fun clearTestResults(
        project: GradlePluginTestProject,
        expectedTaskPaths: Set<String>,
    ) {
        expectedTaskPaths
            .filter { taskPath -> taskPath.endsWith(":testDebugUnitTest") }
            .forEach { taskPath ->
                val projectPath = taskPath.removeSuffix(":testDebugUnitTest")
                val projectDirectory =
                    if (projectPath.isEmpty()) {
                        project.projectDir
                    } else {
                        project.projectDir.resolve(projectPath.removePrefix(":").replace(':', '/'))
                    }
                val testResults = projectDirectory.resolve("build/test-results/testDebugUnitTest")
                if (testResults.exists()) {
                    require(testResults.deleteRecursively()) {
                        "Unable to remove stale JUnit evidence: $testResults"
                    }
                }
            }
    }

    protected fun String.toAbsoluteTaskPath(): String =
        if (startsWith(':')) this else ":$this"

    protected fun String.owningUnitTestTaskPath(): String =
        substringBeforeLast(':') + ":testDebugUnitTest"

    protected fun assertTestClasses(
        project: GradlePluginTestProject,
        projectPath: String = ":",
        includeScreenshot: Boolean,
    ) {
        val classes = project.readExecutedTestClasses(projectPath)
        assertTrue("normal test must execute: $classes", classes.any { it.endsWith(".NormalTest") })
        if (includeScreenshot) {
            assertTrue(
                "Roborazzi test must execute: $classes",
                classes.any { it.endsWith(".RoborazziScreenshotTest") },
            )
        } else {
            assertFalse(
                "Roborazzi test must stay excluded: $classes",
                classes.any { it.endsWith(".RoborazziScreenshotTest") },
            )
        }
    }

    protected fun newProject(name: String): GradlePluginTestProject =
        GradlePluginTestProject.create(
            temporaryFolder.newFolder("$name-root"),
            sharedGradleUserHome,
        )

    protected fun writePng(file: File, argb: Int) {
        require(file.parentFile.mkdirs() || file.parentFile.isDirectory)
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, argb)
        require(ImageIO.write(image, "png", file)) { "PNG writer unavailable" }
    }

    protected companion object {
        const val MAGENTA_ARGB = -65281
    }
}
