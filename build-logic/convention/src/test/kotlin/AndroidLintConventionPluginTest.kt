package com.gasstation.buildlogic

import com.gasstation.buildlogic.testing.AndroidLintFixtureKind
import com.gasstation.buildlogic.testing.GradlePluginTestProject
import com.gasstation.buildlogic.testing.assertTaskOutcome
import com.gasstation.buildlogic.testing.readLintIssues
import com.gasstation.buildlogic.testing.writeAndroidLintFixture
import java.io.File
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidLintConventionPluginTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun testSourcePropertyIsStrictAndParitySafeForApplicationAndLibrary() {
        AndroidLintFixtureKind.entries.forEach { kind ->
            val project = newLintProject("property-${kind.name.lowercase()}", kind)

            val defaultResult = project.runner("lintDebug").build()
            defaultResult.assertTaskOutcome(":lintDebug", TaskOutcome.SUCCESS)
            assertTestOnlyNewApiAbsent(project)

            val explicitFalse =
                project.runner("lintDebug", "-Pgasstation.lintTestSources=false").build()
            assertTrue(
                explicitFalse.task(":lintDebug")?.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE),
            )
            assertTestOnlyNewApiAbsent(project)

        }
    }

    @Test
    fun propertyToggleRegeneratesReportsWithoutStaleTestFindingsForApplicationAndLibrary() {
        AndroidLintFixtureKind.entries.forEach { kind ->
            val project = newLintProject("stale-${kind.name.lowercase()}", kind)

            val productionOnly =
                project.runner("lintDebug", "-Pgasstation.lintTestSources=false").build()
            productionOnly.assertTaskOutcome(":lintDebug", TaskOutcome.SUCCESS)
            assertTestOnlyNewApiAbsent(project)

            val testSources =
                project.runner("lintDebug", "-Pgasstation.lintTestSources=true").buildAndFail()
            testSources.assertTaskOutcome(":lintDebug", TaskOutcome.FAILED)
            assertTestOnlyNewApiPresent(project)

            val productionOnlyAgain =
                project.runner("lintDebug", "-Pgasstation.lintTestSources=false").build()
            productionOnlyAgain.assertTaskOutcome(":lintDebug", TaskOutcome.SUCCESS)
            assertFalse(
                productionOnlyAgain.task(":lintDebug")?.outcome in
                    setOf(TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE, TaskOutcome.SKIPPED),
            )
            assertTestOnlyNewApiAbsent(project)
            assertReportSurface(project)
        }
    }

    @Test
    fun invalidPropertyValuesFailWithStableDiagnostic() {
        val project = newLintProject("invalid-property", AndroidLintFixtureKind.LIBRARY)

        listOf("TRUE", " true", "yes", "").forEach { invalid ->
            val result =
                project.runner("lintDebug", "-Pgasstation.lintTestSources=$invalid").buildAndFail()

            assertTrue(
                result.output.contains("gasstation.lintTestSources must be exactly true or false"),
            )
            assertFalse(result.tasks.any { it.path == ":lintDebug" && it.outcome == TaskOutcome.SUCCESS })
        }
    }

    @Test
    fun fixtureMappingExcludesJvmLibraryFromAndroidLintClaims() {
        assertEquals(
            setOf("gasstation.android.application.compose", "gasstation.android.library"),
            AndroidLintFixtureKind.entries.map(AndroidLintFixtureKind::pluginId).toSet(),
        )
        assertFalse(AndroidLintFixtureKind.entries.any { it.pluginId == "gasstation.jvm.library" })
    }

    private fun newLintProject(
        name: String,
        kind: AndroidLintFixtureKind,
    ): GradlePluginTestProject =
        GradlePluginTestProject.create(temporaryFolder.newFolder("$name-root"))
            .writeAndroidLintFixture(
                kind = kind,
                mainSource = MAIN_SOURCE,
                testSource = TEST_ONLY_NEW_API,
            )

    private fun assertTestOnlyNewApiPresent(project: GradlePluginTestProject) {
        val issue = project.lintXml().readLintIssues().single { it.id == "NewApi" }
        assertEquals("Error", issue.severity)
        assertTrue(issue.file.endsWith("src/test/java/fixture/TestOnlyNewApi.java"))
        assertEquals(7, issue.line)
    }

    private fun assertTestOnlyNewApiAbsent(project: GradlePluginTestProject) {
        assertFalse(project.lintXml().readLintIssues().any { it.id == "NewApi" })
    }

    private fun assertReportSurface(project: GradlePluginTestProject) {
        listOf("xml", "txt", "html", "sarif").forEach { extension ->
            assertTrue("missing lint $extension report", project.lintReport(extension).isFile)
        }
    }

    private fun GradlePluginTestProject.lintXml(): File = lintReport("xml")

    private fun GradlePluginTestProject.lintReport(extension: String): File =
        projectDir.resolve("build/reports/lint-results-debug.$extension")

    companion object {
        private val MAIN_SOURCE =
            """
            package fixture;

            public final class MainSource {
                public String value() { return "fixture"; }
            }
            """.trimIndent()

        private val TEST_ONLY_NEW_API =
            """
            package fixture;

            import android.os.VibrationEffect;

            public final class TestOnlyNewApi {
                public Object create() {
                    return VibrationEffect.createOneShot(10L, 100);
                }
            }
            """.trimIndent()
    }
}
