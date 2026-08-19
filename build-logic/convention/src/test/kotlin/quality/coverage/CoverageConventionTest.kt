package com.gasstation.buildlogic.quality.coverage

import com.gasstation.buildlogic.testing.CoverageFixture
import com.gasstation.buildlogic.testing.GradlePluginTestProject
import com.gasstation.buildlogic.testing.assertConfigurationCacheReused
import com.gasstation.buildlogic.testing.assertConfigurationCacheStored
import com.gasstation.buildlogic.testing.assertTaskOutcome
import com.gasstation.buildlogic.testing.writeCoverageFixture
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.jacoco.core.data.ExecutionData
import org.jacoco.core.data.ExecutionDataWriter

class CoverageConventionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val sharedGradleUserHome by lazy {
        temporaryFolder.newFolder("coverage-gradle-user-home")
    }

    @Test
    fun typedXmlReportAndKotlinSemanticIdentityMatchThePythonGoldenContract() {
        assertEquals("CoverageXmlReportTask", CoverageXmlReportTask::class.simpleName)
        val xml =
            """<report name="sample"><counter type="LINE" missed="1" covered="2"/>""" +
                """<package name="owner"><counter type="BRANCH" missed="3" covered="4"/>""" +
                """<class name="owner/Subject" sourcefilename="Subject.kt">""" +
                """<method name="value" desc="()I" line="7"><counter type="LINE" missed="0" covered="1"/></method>""" +
                """<counter type="LINE" missed="0" covered="1"/></class>""" +
                """<sourcefile name="Subject.kt"><line nr="7" mi="0" ci="1" mb="1" cb="1"/>""" +
                """<counter type="LINE" missed="0" covered="1"/></sourcefile></package></report>"""

        assertEquals(
            "8e247f6b9358b45100493a19b74aeae2e8e4faaa503c17d40da3e380a3937366",
            coverageXmlSemanticSha256(xml.toByteArray(), ":sample|main"),
        )
    }

    @Test
    fun producerPackageLexerSkipsNestedCommentsTextBlocksAndRejectsJavaUnicodeEscapes() {
        val kotlin =
            """
            @file:JvmName("Facade")
            /* package decoy.one /* package decoy.two */ */
            val raw = ${'"'}${'"'}${'"'}package decoy.three${'"'}${'"'}${'"'}
            package real.owner
            class Subject
            """.trimIndent()
        val java =
            """
            /* package decoy.one; */
            class Before { String value = ${'"'}${'"'}${'"'}package decoy.two;${'"'}${'"'}${'"'}; }
            package real.owner;
            """.trimIndent()

        assertEquals("real.owner", lexicalPackageDeclaration(kotlin.toByteArray(), "kt"))
        assertEquals("real.owner", lexicalPackageDeclaration(java.toByteArray(), "java"))
        val error = runCatching {
            lexicalPackageDeclaration("package real\\u002eowner;".toByteArray(), "java")
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("Unicode escape"))
    }

    @Test
    fun executionProducerMergesCompatibleBlocksByProbeOrAndRejectsIncompatibleDuplicates() {
        val compatible = temporaryFolder.newFile("compatible.exec")
        compatible.outputStream().use { output ->
            val writer = ExecutionDataWriter(output)
            writer.visitClassExecution(ExecutionData(1L, "owner/Subject", booleanArrayOf(true, false)))
            writer.visitClassExecution(ExecutionData(1L, "owner/Subject", booleanArrayOf(false, true)))
        }
        val (records, ignored) = readCoverageExecutionRecords(listOf(compatible), setOf("0000000000000001"))
        assertEquals(0, ignored)
        assertEquals("11", records.single()["probes"])

        val incompatible = temporaryFolder.newFile("incompatible.exec")
        incompatible.outputStream().use { output ->
            val writer = ExecutionDataWriter(output)
            writer.visitClassExecution(ExecutionData(1L, "owner/Subject", booleanArrayOf(true)))
            writer.visitClassExecution(ExecutionData(1L, "owner/Other", booleanArrayOf(true)))
        }
        val error = runCatching {
            readCoverageExecutionRecords(listOf(incompatible), setOf("0000000000000001"))
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("Incompatible duplicate"))
    }

    @Test
    fun providerReportsBindJvmAndroidAndBothAppVariantsWithoutBenchmarkOrInternalPaths() {
        val project = newProject("provider-matrix").writeCoverageFixture()
        val sourceCommit = "1".repeat(40)

        val result =
            project.runner(
                "assertCoverageTopology",
                "-Pgasstation.coverageSourceCommit=$sourceCommit",
                "--rerun-tasks",
                "--parallel",
            ).build()

        result.assertTaskOutcome(":coverageXmlReport", TaskOutcome.SUCCESS)
        result.assertTaskOutcome(":assertCoverageTopology", TaskOutcome.SUCCESS)
        listOf(
            ":sample:jvm:test",
            ":android:testDebugUnitTest",
            ":app:testDemoDebugUnitTest",
            ":app:testProdDebugUnitTest",
        ).forEach { path ->
            result.assertTaskOutcome(path, TaskOutcome.SUCCESS)
        }
        assertFalse(result.tasks.any { it.path.startsWith(":benchmark:test") })
        assertFalse(result.tasks.any { it.path.contains("ReleaseUnitTest") })
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
        val sourceCommit = project.git("rev-parse", "HEAD").trim()
        val arguments =
            arrayOf(
                "coverageXmlReport",
                "verifyCoverageReport",
                "-Pgasstation.coverageSourceCommit=$sourceCommit",
                "-Pgasstation.coverageEvent=local",
                "-Pgasstation.coverageBaseRef=${"2".repeat(40)}",
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
        assertTrue(project.stubArgumentsMarker().readText().contains("--base-ref ${"2".repeat(40)}"))
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

        project.projectDir.resolve("sample/jvm/src/main/kotlin/fixture/JvmLogic.kt").appendText("\n")
        project.git("add", ".")
        project.git("commit", "-qm", "move head")
        val stale = project.configurationCacheRunner(*rerunArguments).buildAndFail()
        assertTrue(stale.output.contains("stale source commit"))
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

    @Test
    fun androidCoverageKeepsRobolectricNoLocationClassesInJacocoExecutionData() {
        val project =
            newProject("android-no-location-classes")
                .writeCoverageFixture(CoverageFixture(assertNoLocationClassCollection = true))

        val result =
            project.runner(
                ":android:assertCoverageNoLocationScope",
                ":app:assertCoverageNoLocationScope",
                "-Pgasstation.coverageSourceCommit=${"1".repeat(40)}",
                "--rerun-tasks",
            ).build()

        result.assertTaskOutcome(":android:testDebugUnitTest", TaskOutcome.SUCCESS)
        result.assertTaskOutcome(":app:testDemoDebugUnitTest", TaskOutcome.SUCCESS)
        result.assertTaskOutcome(":app:testProdDebugUnitTest", TaskOutcome.SUCCESS)
        result.assertTaskOutcome(":android:assertCoverageNoLocationScope", TaskOutcome.SUCCESS)
        result.assertTaskOutcome(":app:assertCoverageNoLocationScope", TaskOutcome.SUCCESS)
    }

    @Test
    fun preparedClassProducerRejectsTraversalDuplicatesAndRemovesStaleInputs() {
        val project = newProject("prepared-classes").writeCoverageFixture()
        project.projectDir.resolve("sample/jvm/build.gradle.kts").appendText(
            """

            tasks.named<com.gasstation.buildlogic.quality.coverage.PrepareCoverageClassesTask>("prepareCoverageMainClasses") {
                inputJars.add(layout.projectDirectory.file("fixture-input.jar"))
                inputDirectories.add(layout.projectDirectory.dir("extra-classes"))
            }
            """.trimIndent(),
        )
        val archive = project.projectDir.resolve("sample/jvm/fixture-input.jar")

        writeJar(archive, "../escape.class" to byteArrayOf(1))
        var failed = project.runner(":sample:jvm:prepareCoverageMainClasses", "--rerun-tasks").buildAndFail()
        assertTrue(failed.output.contains("Malformed coverage class path"))

        writeJar(archive, "fixture/JvmLogic.class" to byteArrayOf(2))
        failed = project.runner(":sample:jvm:prepareCoverageMainClasses", "--rerun-tasks").buildAndFail()
        assertTrue(failed.output.contains("Duplicate or case-colliding coverage class path"))

        writeJar(archive, "fixture/Extra.class" to byteArrayOf(3))
        val staleInput = project.projectDir.resolve("sample/jvm/extra-classes/fixture/Stale.class")
        staleInput.parentFile.mkdirs()
        staleInput.writeBytes(byteArrayOf(4))
        project.runner(":sample:jvm:prepareCoverageMainClasses", "--rerun-tasks").build()
        val staleOutput =
            project.projectDir.resolve("sample/jvm/build/reports/coverage/main/prepared-classes/fixture/Stale.class")
        assertTrue(staleOutput.isFile)

        staleInput.delete()
        project.runner(":sample:jvm:prepareCoverageMainClasses", "--rerun-tasks").build()
        assertFalse("removed input survived in prepared output", staleOutput.exists())
    }

    @Test
    fun generatedSourceRootIsRejectedFromAuthoredManifestInventory() {
        val project = newProject("generated-source").writeCoverageFixture()
        project.projectDir.resolve("sample/jvm/build.gradle.kts").appendText(
            """

            sourceSets.named("main") {
                java.srcDir("generated/java")
            }
            """.trimIndent(),
        )
        project.projectDir.resolve("sample/jvm/generated/java/fixture/Generated.java").apply {
            parentFile.mkdirs()
            writeText("package fixture; public final class Generated {}")
        }

        val result =
            project.runner(
                ":sample:jvm:writeCoverageMainManifestEntry",
                "-Pgasstation.coverageSourceCommit=${project.git("rev-parse", "HEAD").trim()}",
                "--rerun-tasks",
            ).buildAndFail()

        assertTrue(result.output.contains("Generated or test source cannot enter authored production coverage"))
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

    private fun GradlePluginTestProject.stubArgumentsMarker(): File =
        projectDir.resolve("build/reports/coverage/stub-arguments.txt")

    private fun GradlePluginTestProject.git(vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git") + arguments).directory(projectDir).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "git ${arguments.joinToString(" ")} failed: $output" }
        return output
    }

    private fun writeJar(file: File, vararg entries: Pair<String, ByteArray>) {
        file.parentFile.mkdirs()
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (path, bytes) ->
                output.putNextEntry(ZipEntry(path))
                output.write(bytes)
                output.closeEntry()
            }
        }
    }
}
