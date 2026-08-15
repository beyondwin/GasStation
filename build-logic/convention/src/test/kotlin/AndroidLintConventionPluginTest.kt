package com.gasstation.buildlogic

import com.gasstation.buildlogic.testing.AndroidLintFixtureKind
import com.gasstation.buildlogic.testing.GradlePluginTestProject
import com.gasstation.buildlogic.testing.LintIssue
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
            assertReportSurface(project)

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

    @Test
    fun warningPromotionFailsForApplicationAndLibrary() {
        AndroidLintFixtureKind.entries.forEach { kind ->
            val project =
                GradlePluginTestProject.create(
                    temporaryFolder.newFolder("warning-${kind.name.lowercase()}-root"),
                ).writeAndroidLintFixture(
                    kind = kind,
                    mainSource = MAIN_WARNING,
                )

            val result = project.runner("lintDebug").buildAndFail()

            result.assertTaskOutcome(":lintDebug", TaskOutcome.FAILED)
            val issues = project.lintXml().readLintIssues()
            assertEquals(setOf("SetTextI18n"), issues.map(LintIssue::id).toSet())
            val issue = issues.single { it.id == "SetTextI18n" }
            assertEquals("SetTextI18n", issue.id)
            assertEquals("Error", issue.severity)
            assertTrue(issue.file.endsWith("src/main/java/fixture/MainSource.java"))
            assertEquals(7, issue.line)
            assertReportSurface(project)
        }
    }

    @Test
    fun reviewedBaselineSuppressesOnlyItsExactWarningLocation() {
        val project =
            GradlePluginTestProject.create(temporaryFolder.newFolder("baseline-isolation-root"))
                .writeAndroidLintFixture(
                    kind = AndroidLintFixtureKind.LIBRARY,
                    mainSource = MAIN_WARNING,
                    lintBaseline = REVIEWED_WARNING_BASELINE,
                )

        val reviewedOnly = project.runner("lintDebug").build()
        reviewedOnly.assertTaskOutcome(":lintDebug", TaskOutcome.SUCCESS)
        assertTrue(assertReviewedBaselineApplied(project).isEmpty())

        project.writeFile(UNREVIEWED_WARNING_PATH, SECOND_WARNING)
        val newWarning = project.runner("lintDebug").buildAndFail()
        newWarning.assertTaskOutcome(":lintDebug", TaskOutcome.FAILED)
        val issues = assertReviewedBaselineApplied(project)
        assertFalse(issues.any { it.file.endsWith("src/main/java/fixture/MainSource.java") })
        val issue = issues.single()
        assertEquals("SetTextI18n", issue.id)
        assertTrue(issue.file.endsWith(UNREVIEWED_WARNING_PATH))
        assertEquals(7, issue.line)
    }

    @Test
    fun reviewedWarningBaselineDoesNotHideANewError() {
        val project =
            GradlePluginTestProject.create(temporaryFolder.newFolder("baseline-error-root"))
                .writeAndroidLintFixture(
                    kind = AndroidLintFixtureKind.APPLICATION,
                    mainSource = MAIN_WARNING,
                    resources = mapOf(NEW_ERROR_PATH to NEW_ERROR_SOURCE),
                    lintBaseline = REVIEWED_WARNING_BASELINE,
                )

        val result = project.runner("lintDebug").buildAndFail()

        result.assertTaskOutcome(":lintDebug", TaskOutcome.FAILED)
        val issue = assertReviewedBaselineApplied(project).single()
        assertEquals("NewApi", issue.id)
        assertEquals("Error", issue.severity)
        assertTrue(issue.file.endsWith(NEW_ERROR_PATH))
        assertEquals(7, issue.line)
        assertReportSurface(project)
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

    private fun assertReviewedBaselineApplied(project: GradlePluginTestProject): List<LintIssue> {
        val issues = project.lintXml().readLintIssues()
        val baselineHint = issues.single { it.id == "LintBaseline" }
        assertEquals("Hint", baselineHint.severity)
        assertTrue(baselineHint.file.endsWith("lint-baseline.xml"))
        assertTrue(baselineHint.message.contains("1 error was filtered out"))
        return issues.filterNot { it.id == "LintBaseline" }
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

        private val MAIN_WARNING =
            """
            package fixture;

            import android.widget.TextView;

            public final class MainSource {
                public void bind(TextView view) {
                    view.setText("fixture");
                }
            }
            """.trimIndent()

        private const val UNREVIEWED_WARNING_PATH = "src/main/java/fixture/SecondWarning.java"
        private val SECOND_WARNING =
            """
            package fixture;

            import android.widget.TextView;

            public final class SecondWarning {
                public void bind(TextView view) {
                    view.setText("second fixture");
                }
            }
            """.trimIndent()

        private const val NEW_ERROR_PATH = "src/main/java/fixture/NewError.java"
        private val NEW_ERROR_SOURCE =
            """
            package fixture;

            import android.os.VibrationEffect;

            public final class NewError {
                public Object create() {
                    return VibrationEffect.createOneShot(10L, 100);
                }
            }
            """.trimIndent()

        private val REVIEWED_WARNING_BASELINE =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <issues format="6" by="lint fixture">
                <issue
                    id="SetTextI18n"
                    message="String literal in `setText` can not be translated. Use Android resources instead.">
                    <location
                        file="src/main/java/fixture/MainSource.java"
                        line="7" />
                </issue>
            </issues>
            """.trimIndent()
    }
}
