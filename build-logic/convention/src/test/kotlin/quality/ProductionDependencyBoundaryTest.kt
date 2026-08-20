package com.gasstation.buildlogic.quality

import com.gasstation.buildlogic.testing.GradlePluginTestProject
import com.gasstation.buildlogic.testing.TestedTargetMutation
import com.gasstation.buildlogic.testing.assertConfigurationCacheReused
import com.gasstation.buildlogic.testing.assertConfigurationCacheStored
import com.gasstation.buildlogic.testing.assertTaskOutcome
import com.gasstation.buildlogic.testing.writeProductionDependencyAndroidFixture
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProductionDependencyBoundaryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val sharedGradleUserHome by lazy {
        temporaryFolder.newFolder("production-dependency-gradle-user-home")
    }

    @Test
    fun realAgpAndResolvedGraphContractsAreCoveredByOneMultiProjectBuild() = runConsolidatedEvidenceOnce()

    @Test
    fun moduleGuardCapturesApiImplementationNestedProjectsAndSortedUniqueViolations() =
        runConsolidatedEvidenceOnce()

    @Test
    fun failingModuleGuardReusesConfigurationCacheAndReproducesPolicyEvidence() =
        runConsolidatedEvidenceOnce()

    private fun runConsolidatedEvidenceOnce() {
        synchronized(consolidatedEvidenceLock) {
            if (consolidatedEvidenceCompleted) return
            runConsolidatedEvidence()
            consolidatedEvidenceCompleted = true
        }
    }

    private fun runConsolidatedEvidence() {
        val project = newProject("tested-target-all-invalid")
            .writeProductionDependencyAndroidFixture(TestedTargetMutation.ALL_INVALID)

        val selectedCheckArguments =
            arrayOf(
                ":core:model:check",
                ":core:observability:check",
                ":domain:location:check",
                ":domain:settings:check",
                ":domain:station:check",
                "--rerun-tasks",
            )
        val selectedChecks = project.runner(*selectedCheckArguments).build()
        assertContractAbiOutcomes(selectedChecks)
        assertContractCheckOutcomes(selectedChecks)
        assertFalse(selectedChecks.tasks.any { it.path.endsWith(":updateKotlinAbi") })

        val arguments =
            arrayOf(
                "verifyModuleBoundaries",
                "productionDependencyInventory",
                ":core:model:checkKotlinAbi",
                ":core:observability:checkKotlinAbi",
                ":domain:location:checkKotlinAbi",
                ":domain:settings:checkKotlinAbi",
                ":domain:station:checkKotlinAbi",
                "verifyPublicApiBoundaries",
                "wiredUpdateMutation",
                "--continue",
                "--rerun-tasks",
            )
        val result = project.configurationCacheRunner(*arguments).buildAndFail()

        assertTrue(result.output, result.tasks.isNotEmpty())
        result.assertTaskOutcome(":verifyModuleBoundaries", TaskOutcome.FAILED)
        result.assertTaskOutcome(":productionDependencyInventory", TaskOutcome.FAILED)
        assertTrue(result.output, result.task(":verifyPublicApiBoundaries") != null)
        result.assertTaskOutcome(":verifyPublicApiBoundaries", TaskOutcome.FAILED)
        assertContractAbiOutcomes(result)
        result.assertConfigurationCacheStored()
        result.assertTaskOutcome(":core:model:updateKotlinAbi", TaskOutcome.FAILED)
        assertTrue(result.output, result.output.contains("updateKotlinAbi automation is forbidden"))
        assertEquals(
            listOf(":core:model:updateKotlinAbi"),
            result.tasks.map { it.path }.filter { it.endsWith(":updateKotlinAbi") },
        )
        assertTrue(result.output, result.output.contains("tested-target relation mismatch"))
        assertTrue(result.output, result.output.contains("tested-target-observation|:benchmark-invalid"))
        assertTrue(result.output, result.output.contains("targets=:app,:app,:core:model,:other-app"))
        assertTrue(result.output, result.output.contains("targets=-"))
        listOf("extra", "missing", "duplicate", "changed").forEach { mutation ->
            assertTrue(
                result.output,
                result.output.contains("tested-target-observation|:benchmark-compile-$mutation"),
            )
        }
        assertTrue(
            result.output,
            result.output.contains(
                "tested-target-observation|:benchmark-compile-extra|benchmark,debug|targets=:app|" +
                    "self-instrumenting=false|compile-only-components=benchmark,debug|" +
                    "target-configuration=benchmark:compileOnly->:app@targetConfiguration=default," +
                    "benchmark:compileOnly->:core:model@targetConfiguration=default," +
                    "debug:compileOnly->:app@targetConfiguration=default," +
                    "debug:compileOnly->:core:model@targetConfiguration=default|" +
                    "multiplicity=benchmark:2,debug:2",
            ),
        )
        assertTrue(
            result.output,
            result.output.contains(
                "tested-target-observation|:benchmark-compile-missing|benchmark,debug|targets=:app|" +
                    "self-instrumenting=false|compile-only-components=-|target-configuration=-|" +
                    "multiplicity=benchmark:0,debug:0",
            ),
        )
        assertTrue(
            result.output,
            result.output.contains(
                "tested-target-observation|:benchmark-compile-duplicate|benchmark,debug|targets=:app|" +
                    "self-instrumenting=false|compile-only-components=benchmark,debug|" +
                    "target-configuration=benchmark:compileOnly->:app@targetConfiguration=default," +
                    "benchmark:compileOnly->:app@targetConfiguration=default," +
                    "debug:compileOnly->:app@targetConfiguration=default," +
                    "debug:compileOnly->:app@targetConfiguration=default|" +
                    "multiplicity=benchmark:2,debug:2",
            ),
        )
        assertTrue(
            result.output,
            result.output.contains(
                "tested-target-observation|:benchmark-compile-changed|benchmark,debug|targets=:app|" +
                    "self-instrumenting=false|compile-only-components=-|" +
                    "target-configuration=benchmark:compileOnly->:other-app@targetConfiguration=default," +
                    "debug:compileOnly->:other-app@targetConfiguration=default|" +
                    "multiplicity=benchmark:1,debug:1",
            ),
        )
        assertTrue(result.output, result.output.contains("expected=tested-target|:benchmark-invalid|benchmark,debug|:app"))
        assertTrue(result.output.contains("unresolved production dependency graph entries"))
        val boundaryReport = project.projectDir.resolve("build/reports/quality/module-boundaries.json").readText()
        assertTrue(boundaryReport.contains(":app|debug"))
        assertTrue(boundaryReport.contains(":library|demoRelease"))
        val directDiagnostics =
            Regex("unallowlisted direct production declaration: [^\"\\n]+")
                .findAll(boundaryReport)
                .map { it.value }
                .toList()
        assertEquals(
            listOf(
                "unallowlisted direct production declaration: scope|:domain:sample|project|:core:network|api|compile=main|runtime=main",
                "unallowlisted direct production declaration: scope|:feature:nested:sample|project|:core:database|implementation|compile=main|runtime=main",
                "unallowlisted direct production declaration: scope|:feature:sample|project|:data:sample|implementation|compile=main|runtime=main",
            ),
            directDiagnostics,
        )
        listOf(
            "scope|:feature:sample|project|:core:database|compileOnly|compile=main|runtime=-",
            "scope|:feature:sample|project|:core:network|compileOnlyApi|compile=main|runtime=-",
            "scope|:feature:sample|project|:domain:sample|runtimeOnly|compile=-|runtime=main",
            "scope|:library|project|:core:model|demoImplementation|compile=demoDebug,demoRelease|runtime=demoDebug,demoRelease",
            "scope|:library|project|:core:observability|debugImplementation|compile=demoDebug,prodDebug|runtime=demoDebug,prodDebug",
            "scope|:library|project|:domain:location|demoDebugImplementation|compile=demoDebug|runtime=demoDebug",
        ).forEach { scope -> assertTrue(boundaryReport, boundaryReport.contains(scope)) }
        listOf("testImplementation", "androidTestImplementation", "|ksp|").forEach { excluded ->
            assertFalse(boundaryReport, boundaryReport.contains(excluded))
        }
        assertTrue(boundaryReport.contains(":benchmark-valid-true|benchmark"))
        assertTrue(
            boundaryReport.contains(
                "tested-target|:benchmark-valid-true|benchmark,debug|:app|" +
                    "self-instrumenting=true|compile-only-membership=absent|compile-only-identities=-",
            ),
        )
        assertTrue(
            boundaryReport.contains(
                "tested-target|:benchmark-valid-false|benchmark,debug|:app|" +
                    "self-instrumenting=false|compile-only-membership=present|compile-only-identities=",
            ),
        )
        val report = project.projectDir.resolve("build/reports/quality/production-dependency-graph.json").readText()
        assertTrue(report.contains("|root|root=project:"))
        assertTrue(report.contains("path=project::domain:station>project::core:observability"))
        assertTrue(report.contains("path=project::domain:station>project::domain:location>project::core:observability"))
        assertTrue(report.contains("path=project::domain:station>project::domain:settings>project::core:observability"))
        assertTrue(report.contains("path=project::graph:a>project::graph:b>project::graph:a"))
        assertTrue(report.contains("|unresolved|root=project:"))
        assertTrue(report.contains("parent=project::graph:unresolved"))
        assertTrue(report.contains("requested=invalid.example:never-resolve:1.0"))
        assertTrue(report.contains("path=project::graph:unresolved>unresolved:invalid.example:never-resolve:1.0"))
        val publicApiReport =
            project.projectDir.resolve("build/reports/quality/public-api-boundaries.json").readText()
        assertTrue(publicApiReport, publicApiReport.contains("transform"))
        assertTrue(publicApiReport, publicApiReport.contains("method-signature|android.os.Parcelable"))
        assertTrue(publicApiReport, publicApiReport.contains("android.os.Parcelable"))
        assertTrue(publicApiReport, publicApiReport.contains("Lkotlin/jvm/functions/Function1"))
        assertTrue(publicApiReport, publicApiReport.contains("Lkotlin/coroutines/Continuation"))
        assertFalse(publicApiReport, publicApiReport.contains("missing required method Signature"))

        val firstBoundaryReport = boundaryReport.toByteArray()
        val firstInventoryReport = report.toByteArray()
        val replay = project.configurationCacheRunner(*arguments).buildAndFail()
        replay.assertConfigurationCacheReused()
        replay.assertTaskOutcome(":verifyModuleBoundaries", TaskOutcome.FAILED)
        replay.assertTaskOutcome(":productionDependencyInventory", TaskOutcome.FAILED)
        replay.assertTaskOutcome(":verifyPublicApiBoundaries", TaskOutcome.FAILED)
        assertContractAbiOutcomes(replay)
        replay.assertTaskOutcome(":core:model:updateKotlinAbi", TaskOutcome.FAILED)
        assertEquals(
            listOf(":core:model:updateKotlinAbi"),
            replay.tasks.map { it.path }.filter { it.endsWith(":updateKotlinAbi") },
        )
        assertArrayEquals(
            firstBoundaryReport,
            project.projectDir.resolve("build/reports/quality/module-boundaries.json").readBytes(),
        )
        assertArrayEquals(
            firstInventoryReport,
            project.projectDir.resolve("build/reports/quality/production-dependency-graph.json").readBytes(),
        )
    }

    private fun assertContractAbiOutcomes(result: org.gradle.testkit.runner.BuildResult) {
        listOf(":core:model", ":core:observability", ":domain:location", ":domain:settings", ":domain:station")
            .forEach { module -> result.assertTaskOutcome("$module:checkKotlinAbi", TaskOutcome.SUCCESS) }
    }

    private fun assertContractCheckOutcomes(result: org.gradle.testkit.runner.BuildResult) {
        listOf(":core:model", ":core:observability", ":domain:location", ":domain:settings", ":domain:station")
            .forEach { module -> result.assertTaskOutcome("$module:check", TaskOutcome.SUCCESS) }
    }

    private fun newProject(name: String): GradlePluginTestProject =
        GradlePluginTestProject.create(
            temporaryFolder.newFolder("$name-root"),
            sharedGradleUserHome,
        )

    private companion object {
        val consolidatedEvidenceLock = Any()

        @Volatile
        var consolidatedEvidenceCompleted = false
    }
}
