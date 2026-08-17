package com.gasstation.buildlogic.quality.coverage

import com.gasstation.buildlogic.testing.CoverageFixture
import com.gasstation.buildlogic.testing.GradlePluginTestProject
import com.gasstation.buildlogic.testing.assertConfigurationCacheReused
import com.gasstation.buildlogic.testing.assertConfigurationCacheStored
import com.gasstation.buildlogic.testing.assertTaskOutcome
import com.gasstation.buildlogic.testing.writeCoverageFixture
import java.io.File
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CoverageConventionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val sharedGradleUserHome by lazy {
        temporaryFolder.newFolder("coverage-gradle-user-home")
    }

    @Test
    fun providerReportsBindJvmAndroidAndBothAppVariantsWithoutBenchmarkOrInternalPaths() {
        val project = newProject("provider-matrix").writeCoverageFixture()
        val sourceCommit = "1".repeat(40)

        val result =
            project.runner(
                "coverageXmlReport",
                "-Pgasstation.coverageSourceCommit=$sourceCommit",
                "--rerun-tasks",
                "--parallel",
            ).build()

        result.assertTaskOutcome(":coverageXmlReport", TaskOutcome.SUCCESS)
        listOf(
            ":sample:jvm:test",
            ":android:testDebugUnitTest",
            ":app:testDemoDebugUnitTest",
            ":app:testProdDebugUnitTest",
        ).forEach { path ->
            result.assertTaskOutcome(path, TaskOutcome.SUCCESS)
        }
        assertFalse(result.tasks.any { it.path.startsWith(":benchmark:test") })
        val manifest = project.projectDir.resolve("build/reports/coverage/report-manifest.json")
        assertTrue("coverage manifest missing", manifest.isFile)
        val manifestText = manifest.readText()
        assertEquals(4, Regex("manifest-entry\\.json").findAll(manifestText).count())
        val entryText = project.entryFiles().joinToString("\n", transform = File::readText)
        listOf(":sample:jvm|main", ":android|debug", ":app|demoDebug", ":app|prodDebug")
            .forEach { assertTrue("missing report $it", entryText.contains(it)) }
        assertTrue(manifestText.contains(":benchmark"))
        assertFalse(manifestText.contains("built_in_kotlinc"))
        assertFalse(manifestText.contains("classes/kotlin/main"))
        assertFalse(manifestText.contains(project.projectDir.absolutePath))
        project.entryFiles().forEach { entry ->
            val text = entry.readText()
            assertTrue(text.contains("\"classFileCount\":"))
            assertTrue(text.contains("\"executionSemanticSha256\":"))
            assertTrue(text.contains("\"reportSemanticSha256\":"))
            assertFalse(text.contains(project.projectDir.absolutePath))
        }
    }

    @Test
    fun producersBecomeUpToDateVerifierAlwaysRunsAndConfigurationCacheStoresThenReuses() {
        val project = newProject("cache-and-always-run").writeCoverageFixture()
        val sourceCommit = "1".repeat(40)
        val arguments =
            arrayOf(
                "coverageXmlReport",
                "verifyCoverageReport",
                "-Pgasstation.coverageSourceCommit=$sourceCommit",
                "-Pgasstation.coverageEvent=local",
                "--parallel",
            )

        val ordinaryFirst = project.runner(*arguments).build()
        ordinaryFirst.assertTaskOutcome(":coverageXmlReport", TaskOutcome.SUCCESS)
        ordinaryFirst.assertTaskOutcome(":verifyCoverageReport", TaskOutcome.SUCCESS)
        val firstIdentities = project.semanticIdentities()

        val ordinarySecond = project.runner(*arguments).build()
        ordinarySecond.assertTaskOutcome(":verifyCoverageReport", TaskOutcome.SUCCESS)
        assertFalse(
            ordinarySecond.task(":verifyCoverageReport")?.outcome in
                setOf(TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE, TaskOutcome.SKIPPED),
        )
        assertTrue(
            ordinarySecond.tasks.any {
                it.path.contains("coverage") && it.path != ":coverageXmlReport" &&
                    it.path != ":verifyCoverageReport" && it.outcome == TaskOutcome.UP_TO_DATE
            },
        )
        assertEquals("2", project.stubInvocationMarker().readText())
        assertEquals(firstIdentities, project.semanticIdentities())

        val rerunArguments = arguments + "--rerun-tasks"
        val cacheFirst = project.configurationCacheRunner(*rerunArguments).build()
        cacheFirst.assertConfigurationCacheStored()
        cacheFirst.assertTaskOutcome(":verifyCoverageReport", TaskOutcome.SUCCESS)
        val cacheIdentities = project.semanticIdentities()

        val cacheSecond = project.configurationCacheRunner(*rerunArguments).build()
        cacheSecond.assertConfigurationCacheReused()
        cacheSecond.assertTaskOutcome(":verifyCoverageReport", TaskOutcome.SUCCESS)
        assertEquals(cacheIdentities, project.semanticIdentities())
        assertEquals("4", project.stubInvocationMarker().readText())
    }

    @Test
    fun explicitEmptyModuleFailsOwnershipWhileImplicitGroupingProjectIsNotAModule() {
        val project =
            newProject("empty-module")
                .writeCoverageFixture(CoverageFixture(includeUnownedEmptyModule = true))

        val result =
            project.runner(
                "coverageXmlReport",
                "-Pgasstation.coverageSourceCommit=${"1".repeat(40)}",
                "--rerun-tasks",
            ).buildAndFail()

        assertTrue(result.output.contains(":empty"))
        assertTrue(result.output, result.output.contains("coverage owner or reviewed exclusion"))
        assertFalse(result.output.contains(":sample must have a coverage owner"))
    }

    private fun newProject(name: String): GradlePluginTestProject =
        GradlePluginTestProject.create(
            temporaryFolder.newFolder("$name-root"),
            sharedGradleUserHome,
        )

    private fun GradlePluginTestProject.entryFiles(): List<File> =
        projectDir.walkTopDown()
            .filter { it.isFile && it.name == "manifest-entry.json" }
            .toList()
            .sortedBy { it.relativeTo(projectDir).invariantSeparatorsPath }

    private fun GradlePluginTestProject.semanticIdentities(): List<String> =
        entryFiles().flatMap { entry ->
            Regex(
                "\\\"(?:executionSemanticSha256|reportSemanticSha256)\\\":\\\"([0-9a-f]{64})\\\"",
            ).findAll(entry.readText()).map { it.groupValues[1] }.toList()
        }

    private fun GradlePluginTestProject.stubInvocationMarker(): File =
        projectDir.resolve("build/reports/coverage/stub-invocations.txt")
}
