package com.gasstation.buildlogic

import com.gasstation.buildlogic.testing.AndroidLintFixtureKind
import com.gasstation.buildlogic.testing.GradlePluginTestProject
import com.gasstation.buildlogic.testing.LintIssue
import com.gasstation.buildlogic.testing.assertTaskOutcome
import com.gasstation.buildlogic.testing.assertConfigurationCacheReused
import com.gasstation.buildlogic.testing.assertConfigurationCacheStored
import com.gasstation.buildlogic.testing.assertOutputKeyValueExactlyOnce
import com.gasstation.buildlogic.testing.readLintIssues
import com.gasstation.buildlogic.testing.writeAndroidLintFixture
import com.gasstation.buildlogic.testing.writeAndroidLintMultiProjectFixture
import java.io.File
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class AndroidLintPropertySelectionTest : AndroidLintConventionTestSupport() {
    @Test
    fun testSourcePropertyIsStrictAndParitySafeForApplicationAndLibrary() {
        val project = newLintMultiProject("property")
        val lintTasks = AndroidLintFixtureKind.entries.map { it.lintTask() }

        val defaultResult = project.runner(*lintTasks.toTypedArray(), "--parallel").build()
        AndroidLintFixtureKind.entries.forEach { kind ->
            defaultResult.assertTaskOutcome(kind.lintTask(), TaskOutcome.SUCCESS)
            assertTestOnlyNewApiAbsent(project, kind)
        }

        val explicitFalse =
            project.runner(
                *lintTasks.toTypedArray(),
                "--parallel",
                "-Pgasstation.lintTestSources=false",
            ).build()
        AndroidLintFixtureKind.entries.forEach { kind ->
            assertTrue(
                explicitFalse.task(kind.lintTask())?.outcome in
                    setOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE),
            )
            assertTestOnlyNewApiAbsent(project, kind)
        }
    }

    @Test
    fun invalidPropertyUsesSharedStrictParserBeforeLintCanSucceed() {
        val project = newLintProject("invalid-property", AndroidLintFixtureKind.LIBRARY)
        val result =
            project.runner("lintDebug", "-Pgasstation.lintTestSources=TRUE").buildAndFail()

        assertTrue(
            result.output.contains("gasstation.lintTestSources must be exactly true or false"),
        )
        assertFalse(result.tasks.any { it.path == ":lintDebug" && it.outcome == TaskOutcome.SUCCESS })
    }

    @Test
    fun fixtureMappingExcludesJvmLibraryFromAndroidLintClaims() {
        assertEquals(
            setOf("gasstation.android.application.compose", "gasstation.android.library"),
            AndroidLintFixtureKind.entries.map(AndroidLintFixtureKind::pluginId).toSet(),
        )
        assertFalse(AndroidLintFixtureKind.entries.any { it.pluginId == "gasstation.jvm.library" })
    }
}

internal class AndroidLintReportRegenerationTest : AndroidLintConventionTestSupport() {
    @Test
    fun propertyToggleRegeneratesReportsWithoutStaleTestFindingsForApplicationAndLibrary() {
        val isolationSentinel = newTestProject("stale-isolation-sentinel")
        val project = newLintMultiProject("stale")
        assertEquals(
            isolationSentinel.gradleUserHomeDir.canonicalFile,
            project.gradleUserHomeDir.canonicalFile,
        )
        assertFalse(isolationSentinel.projectDir.canonicalFile == project.projectDir.canonicalFile)
        assertFalse(isolationSentinel.testKitDir.canonicalFile == project.testKitDir.canonicalFile)
        AndroidLintFixtureKind.entries.forEach { kind -> assertFalse(project.lintXml(kind).exists()) }
        val lintTasks = AndroidLintFixtureKind.entries.map { it.lintTask() }

        val productionOnly =
            project.runner(
                *lintTasks.toTypedArray(),
                "--parallel",
                "-Pgasstation.lintTestSources=false",
            ).build()
        AndroidLintFixtureKind.entries.forEach { kind ->
            productionOnly.assertTaskOutcome(kind.lintTask(), TaskOutcome.SUCCESS)
            assertTestOnlyNewApiAbsent(project, kind)
        }

        val testSources =
            project.runner(
                *lintTasks.toTypedArray(),
                "--continue",
                "--parallel",
                "-Pgasstation.lintTestSources=true",
            ).buildAndFail()
        AndroidLintFixtureKind.entries.forEach { kind ->
            testSources.assertTaskOutcome(kind.lintTask(), TaskOutcome.FAILED)
            assertTestOnlyNewApiPresent(project, kind)
            assertReportSurface(project, kind)
        }

        val productionOnlyAgain =
            project.runner(
                *lintTasks.toTypedArray(),
                "--parallel",
                "-Pgasstation.lintTestSources=false",
            ).build()
        AndroidLintFixtureKind.entries.forEach { kind ->
            productionOnlyAgain.assertTaskOutcome(kind.lintTask(), TaskOutcome.SUCCESS)
            assertFalse(
                productionOnlyAgain.task(kind.lintTask())?.outcome in
                    setOf(TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE, TaskOutcome.SKIPPED),
            )
            assertTestOnlyNewApiAbsent(project, kind)
            assertReportSurface(project, kind)
        }
    }
}

internal class AndroidLintManagedDevicesTest : AndroidLintConventionTestSupport() {
    @Test
    fun applicationAndLibraryExposeOnlyTheReviewedManagedDevicesAndReuseConfigurationCache() {
        val project = newLintMultiProject("managed-devices")
        val arguments =
            arrayOf(
                ":application:tasks",
                ":library:tasks",
                "--group=verification",
            )

        val first = project.configurationCacheRunner(*arguments).build()
        first.assertConfigurationCacheStored()
        assertManagedDeviceContract(first.output)

        val second = project.configurationCacheRunner(*arguments).build()
        second.assertConfigurationCacheReused()
        assertManagedDeviceTasks(second.output)
    }
}

internal class AndroidLintWarningPromotionTest : AndroidLintConventionTestSupport() {
    @Test
    fun warningPromotionFailsForApplicationAndLibrary() {
        val project =
            newTestProject("warning-matrix").writeAndroidLintMultiProjectFixture(
                mainSource = MAIN_WARNING,
            )
        val lintTasks = AndroidLintFixtureKind.entries.map { it.lintTask() }
        val result =
            project.runner(*lintTasks.toTypedArray(), "--continue", "--parallel").buildAndFail()

        AndroidLintFixtureKind.entries.forEach { kind ->
            result.assertTaskOutcome(kind.lintTask(), TaskOutcome.FAILED)
            val issues = project.lintXml(kind).readLintIssues()
            assertEquals(setOf("SetTextI18n"), issues.map(LintIssue::id).toSet())
            val issue = issues.single { it.id == "SetTextI18n" }
            assertEquals("SetTextI18n", issue.id)
            assertEquals("Error", issue.severity)
            assertTrue(issue.file.endsWith("src/main/java/fixture/MainSource.java"))
            assertEquals(7, issue.line)
            assertReportSurface(project, kind)
        }
    }
}

internal class AndroidLintBaselineIsolationTest : AndroidLintConventionTestSupport() {
    @Test
    fun reviewedBaselineSuppressesOnlyItsExactWarningLocation() {
        val project =
            newTestProject("baseline-isolation")
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
            newTestProject("baseline-error")
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
}

internal abstract class AndroidLintConventionTestSupport {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    protected val sharedGradleUserHome by lazy {
        temporaryFolder.newFolder("android-lint-gradle-user-home")
    }

    protected fun newLintProject(
        name: String,
        kind: AndroidLintFixtureKind,
    ): GradlePluginTestProject =
        newTestProject(name)
            .writeAndroidLintFixture(
                kind = kind,
                mainSource = MAIN_SOURCE,
                testSource = TEST_ONLY_NEW_API,
            )

    protected fun newLintMultiProject(name: String): GradlePluginTestProject =
        newTestProject(name).writeAndroidLintMultiProjectFixture(
            mainSource = MAIN_SOURCE,
            testSource = TEST_ONLY_NEW_API,
        )

    protected fun newTestProject(name: String): GradlePluginTestProject =
        GradlePluginTestProject.create(
            temporaryFolder.newFolder("$name-root"),
            sharedGradleUserHome,
        )

    protected fun assertTestOnlyNewApiPresent(
        project: GradlePluginTestProject,
        kind: AndroidLintFixtureKind? = null,
    ) {
        val issue = project.lintXml(kind).readLintIssues().single { it.id == "NewApi" }
        assertEquals("Error", issue.severity)
        assertTrue(issue.file.endsWith("src/test/java/fixture/TestOnlyNewApi.java"))
        assertEquals(7, issue.line)
    }

    protected fun assertTestOnlyNewApiAbsent(
        project: GradlePluginTestProject,
        kind: AndroidLintFixtureKind? = null,
    ) {
        assertFalse(project.lintXml(kind).readLintIssues().any { it.id == "NewApi" })
    }

    protected fun assertReportSurface(
        project: GradlePluginTestProject,
        kind: AndroidLintFixtureKind? = null,
    ) {
        listOf("xml", "txt", "html", "sarif").forEach { extension ->
            assertTrue("missing lint $extension report", project.lintReport(extension, kind).isFile)
        }
    }

    protected fun assertManagedDeviceContract(output: String) {
        val expected =
            "gasstationPixel2Api28|Pixel 2|28|aosp," +
                "gasstationPixel2Api36|Pixel 2|36|google"
        assertEquals(2, Regex("(?m)^MANAGED_DEVICES=").findAll(output).count())
        assertEquals(2, Regex("(?m)^MANAGED_GROUPS=$").findAll(output).count())
        assertEquals(2, Regex("(?m)^MANAGED_DEVICES=${Regex.escape(expected)}$").findAll(output).count())
        assertManagedDeviceTasks(output)
    }

    protected fun assertManagedDeviceTasks(output: String) {
        assertTrue(output.contains("gasstationPixel2Api28DebugAndroidTest"))
        assertTrue(output.contains("gasstationPixel2Api36DebugAndroidTest"))
        assertFalse(output.contains("gasstationPixel2Api24"))
        assertFalse(output.contains("managedDeviceGroup"))
    }

    protected fun assertReviewedBaselineApplied(project: GradlePluginTestProject): List<LintIssue> {
        val issues = project.lintXml().readLintIssues()
        val baselineHint = issues.single { it.id == "LintBaseline" }
        assertEquals("Hint", baselineHint.severity)
        assertTrue(baselineHint.file.endsWith("lint-baseline.xml"))
        assertTrue(baselineHint.message.contains("1 error was filtered out"))
        return issues.filterNot { it.id == "LintBaseline" }
    }

    protected fun AndroidLintFixtureKind.lintTask(): String = ":${name.lowercase()}:lintDebug"

    protected fun GradlePluginTestProject.lintXml(
        kind: AndroidLintFixtureKind? = null,
    ): File = lintReport("xml", kind)

    protected fun GradlePluginTestProject.lintReport(
        extension: String,
        kind: AndroidLintFixtureKind? = null,
    ): File =
        projectDir.resolve(
            (kind?.name?.lowercase()?.plus("/") ?: "") +
                "build/reports/lint-results-debug.$extension",
        )

    companion object {
        internal val MAIN_SOURCE =
            """
            package fixture;

            public final class MainSource {
                public String value() { return "fixture"; }
            }
            """.trimIndent()

        internal val TEST_ONLY_NEW_API =
            """
            package fixture;

            import android.os.VibrationEffect;

            public final class TestOnlyNewApi {
                public Object create() {
                    return VibrationEffect.createOneShot(10L, 100);
                }
            }
            """.trimIndent()

        internal val MAIN_WARNING =
            """
            package fixture;

            import android.widget.TextView;

            public final class MainSource {
                public void bind(TextView view) {
                    view.setText("fixture");
                }
            }
            """.trimIndent()

        protected const val UNREVIEWED_WARNING_PATH = "src/main/java/fixture/SecondWarning.java"
        internal val SECOND_WARNING =
            """
            package fixture;

            import android.widget.TextView;

            public final class SecondWarning {
                public void bind(TextView view) {
                    view.setText("second fixture");
                }
            }
            """.trimIndent()

        protected const val NEW_ERROR_PATH = "src/main/java/fixture/NewError.java"
        internal val NEW_ERROR_SOURCE =
            """
            package fixture;

            import android.os.VibrationEffect;

            public final class NewError {
                public Object create() {
                    return VibrationEffect.createOneShot(10L, 100);
                }
            }
            """.trimIndent()

        internal val REVIEWED_WARNING_BASELINE =
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
