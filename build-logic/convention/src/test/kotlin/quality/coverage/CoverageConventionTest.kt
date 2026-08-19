package com.gasstation.buildlogic.quality.coverage

import com.gasstation.buildlogic.testing.CoverageFixture
import com.gasstation.buildlogic.testing.GradlePluginTestProject
import com.gasstation.buildlogic.testing.assertConfigurationCacheReused
import com.gasstation.buildlogic.testing.assertConfigurationCacheStored
import com.gasstation.buildlogic.testing.assertTaskOutcome
import com.gasstation.buildlogic.testing.writeCoverageFixture
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
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
        val invalidInputs = listOf(
            arrayOf<Any>(0, ":android:testDebugUnitTest", ":android:testDebugUnitTest", 1, true) to
                "requires exactly one selected unit-test task",
            arrayOf<Any>(1, ":android:wrongTest", ":android:testDebugUnitTest", 1, true) to
                "test task identity mismatch",
            arrayOf<Any>(1, ":android:testDebugUnitTest", ":android:testDebugUnitTest", 0, true) to
                "requires exactly one existing JaCoCo execution file",
            arrayOf<Any>(1, ":android:testDebugUnitTest", ":android:testDebugUnitTest", 1, false) to
                "prepared class directory is empty",
        )
        invalidInputs.forEach { (values, expected) ->
            val error = runCatching {
                validateCoverageXmlReportInputs(
                    ":android|debug",
                    values[0] as Int,
                    values[1] as String,
                    values[2] as String,
                    values[3] as Int,
                    values[4] as Boolean,
                )
            }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains(expected))
        }
        val fixtures = File("../../scripts/quality/tests/fixtures/coverage").canonicalFile
        val xml = fixtures.resolve("semantic-golden.xml").readBytes()
        val swapped = fixtures.resolve("semantic-equal-counter-swap.xml").readBytes()

        assertEquals(
            "8e247f6b9358b45100493a19b74aeae2e8e4faaa503c17d40da3e380a3937366",
            coverageXmlSemanticSha256(xml, ":sample|main"),
        )
        assertFalse(coverageXmlSemanticRecords(xml, ":sample|main").contentEquals(coverageXmlSemanticRecords(swapped, ":sample|main")))
        assertFalse(coverageXmlSemanticSha256(xml, ":sample|main") == coverageXmlSemanticSha256(swapped, ":sample|main"))
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
        val sourceCommit = project.git("rev-parse", "HEAD").trim()
        project.projectDir.resolve("android/build.gradle.kts").appendText(
            """

            tasks.register<Test>("unrelatedTest") {
                doFirst { error("unrelated test task executed") }
            }
            """.trimIndent(),
        )

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
        assertEquals(null, result.task(":android:unrelatedTest"))
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
            assertTrue(text.contains("\"inputClassArtifacts\":[{\"entryCount\":"))
            assertFalse(text.contains("\"inputClassArtifacts\":[\""))
            assertTrue(text.contains("\"executionSemanticSha256\":"))
            assertTrue(text.contains("\"reportSemanticSha256\":"))
            assertFalse(text.contains(project.projectDir.absolutePath))
        }
        project.assertRealVerifierMutationBoundary()
    }

    @Test
    fun typedXmlReportTaskRejectsLiveCardinalityIdentityExecAndClassMutations() {
        val project = newProject("typed-report-invalid-inputs").writeCoverageFixture(CoverageFixture(jvmOnly = true))
        project.projectDir.resolve("sample/jvm/probe").mkdirs()
        project.projectDir.resolve("sample/jvm/probe/one.exec").writeBytes(byteArrayOf(1))
        project.projectDir.resolve("sample/jvm/probe/two.exec").writeBytes(byteArrayOf(2))
        project.projectDir.resolve("sample/jvm/probe-empty-classes").mkdirs()
        project.projectDir.resolve("sample/jvm/build.gradle.kts").appendText(
            """

            val duplicateCoverageCandidate = tasks.register<org.gradle.api.tasks.testing.Test>("duplicateCoverageCandidate")
            tasks.register<org.gradle.api.tasks.testing.Test>("unrelatedCoverageTest") {
                doFirst { error("unrelated coverage test executed") }
            }
            val coverageProbeCase = providers.gradleProperty("gasstation.coverageProbeCase").orElse("valid")
            val liveCoverageTests = tasks.withType<org.gradle.api.tasks.testing.Test>()
            val observedCoverageCallbackPath = objects.property(String::class.java)
            liveCoverageTests.matching {
                if (coverageProbeCase.get() == "wrong-callback") {
                    it.name == duplicateCoverageCandidate.get().name
                } else {
                    it.name == "test"
                }
            }.configureEach {
                observedCoverageCallbackPath.set(path)
            }
            tasks.named<com.gasstation.buildlogic.quality.coverage.CoverageXmlReportTask>("coverageMainXmlReport") {
                selectedTestTaskCount.set(providers.provider {
                    when (coverageProbeCase.get()) {
                        "zero-task" -> liveCoverageTests.matching { false }.size
                        "two-task" -> liveCoverageTests.matching {
                            it.name == "test" || it.name == duplicateCoverageCandidate.get().name
                        }.size
                        else -> liveCoverageTests.matching { it.name == "test" }.size
                    }
                })
                observedTestTaskPath.set(observedCoverageCallbackPath)
                if (coverageProbeCase.get() == "wrong-task") {
                    expectedTestTaskPath.set("${'$'}{project.path}:wrongExpectedTask")
                }
                if (coverageProbeCase.get() == "zero-exec") {
                    exactExecutionData.setFrom(layout.projectDirectory.file("probe/missing.exec"))
                }
                if (coverageProbeCase.get() == "two-exec") {
                    exactExecutionData.setFrom(
                        layout.projectDirectory.file("probe/one.exec"),
                        layout.projectDirectory.file("probe/two.exec"),
                    )
                }
                if (coverageProbeCase.get() == "empty-classes") {
                    preparedClassDirectory.set(layout.projectDirectory.dir("probe-empty-classes"))
                }
            }
            """.trimIndent(),
        )
        val sourceCommit = project.git("rev-parse", "HEAD").trim()
        val cases =
            listOf(
                "zero-task" to "requires exactly one selected unit-test task; found 0",
                "two-task" to "requires exactly one selected unit-test task; found 2",
                "wrong-callback" to "test task identity mismatch",
                "wrong-task" to "test task identity mismatch",
                "zero-exec" to "requires exactly one existing JaCoCo execution file; found 0",
                "two-exec" to "requires exactly one existing JaCoCo execution file; found 2",
                "empty-classes" to "prepared class directory is empty",
            )

        cases.forEach { (probeCase, diagnostic) ->
            val result =
                project.runner(
                    ":sample:jvm:coverageMainXmlReport",
                    "-Pgasstation.coverageSourceCommit=$sourceCommit",
                    "-Pgasstation.coverageProbeCase=$probeCase",
                    "--rerun-tasks",
                ).buildAndFail()

            result.assertTaskOutcome(":sample:jvm:coverageMainXmlReport", TaskOutcome.FAILED)
            assertTrue("$probeCase did not report $diagnostic\n${result.output}", result.output.contains(diagnostic))
            assertEquals("unrelated test executed for $probeCase", null, result.task(":sample:jvm:unrelatedCoverageTest"))
        }
    }

    @Test
    fun gradleVerifierTaskUsesRealVerifierAndRejectsPostEntryMutations() {
        val project = newProject("real-gradle-verifier").writeCoverageFixture(CoverageFixture(jvmOnly = true))
        project.installRealVerifierArchitecture()
        val architectureCommit = project.git("rev-parse", "HEAD").trim()
        project.runner(
            "coverageXmlReport",
            "-Pgasstation.coverageSourceCommit=$architectureCommit",
            "--rerun-tasks",
        ).build().assertTaskOutcome(":coverageXmlReport", TaskOutcome.SUCCESS)
        val capture =
            project.runProcess(
                "python3",
                "scripts/quality/verify_coverage.py",
                "capture",
                "--manifest",
                "build/reports/coverage/report-manifest.json",
                "--policy",
                "config/quality/coverage-policy.json",
                "--source-commit",
                architectureCommit,
                "--output",
                "config/quality/coverage-baseline.json",
            )
        assertEquals(capture.second, 0, capture.first)
        project.git("add", "config/quality/coverage-baseline.json")
        project.git("commit", "-qm", "capture fixture baseline")
        val sourceCommit = project.git("rev-parse", "HEAD").trim()
        val arguments =
            arrayOf(
                ":verifyCoverageReport",
                "-Pgasstation.coverageSourceCommit=$sourceCommit",
                "-Pgasstation.coverageEvent=local",
                "--rerun-tasks",
            )
        val initial = project.runner(*arguments).build()
        initial.assertTaskOutcome(":verifyCoverageReport", TaskOutcome.SUCCESS)
        assertEquals(null, initial.task(":sample:jvm:unrelatedCoverageTest"))

        val entry = project.entryFiles().single()
        val originalEntry = entry.readBytes()
        val entryText = originalEntry.toString(Charsets.UTF_8)
        val verifyOnly =
            arrayOf(
                ":verifyCoverageReport",
                "-x",
                "coverageXmlReport",
                "-Pgasstation.coverageSourceCommit=$sourceCommit",
                "-Pgasstation.coverageEvent=local",
            )
        fun rejectMutation(diagnostic: String): org.gradle.testkit.runner.BuildResult {
            val result = project.runner(*verifyOnly).buildAndFail()
            result.assertTaskOutcome(":verifyCoverageReport", TaskOutcome.FAILED)
            assertTrue("missing $diagnostic\n${result.output}", result.output.contains(diagnostic))
            return result
        }

        val executionPath = Regex("\\\"executionData\\\":\\[\\\"([^\\\"]+)\\\"\\]").find(entryText)!!.groupValues[1]
        val execution = project.projectDir.resolve(executionPath)
        val originalExecution = execution.readBytes()
        execution.writeBytes(originalExecution + byteArrayOf(0))
        rejectMutation("execution data hash mismatch")
        execution.writeBytes(originalExecution)

        val xmlPath = Regex("\\\"xmlReport\\\":\\\"([^\\\"]+)\\\"").find(entryText)!!.groupValues[1]
        val xml = project.projectDir.resolve(xmlPath)
        val originalXml = xml.readBytes()
        xml.writeBytes(originalXml + " ".toByteArray())
        rejectMutation("XML hash mismatch")
        xml.writeBytes(originalXml)

        val semanticXml =
            Regex("covered=\\\"([1-9][0-9]*)\\\"").replaceFirst(
                originalXml.toString(Charsets.UTF_8),
                "covered=\\\"0\\\"",
            ).toByteArray()
        assertFalse("semantic XML mutation did not change bytes", semanticXml.contentEquals(originalXml))
        xml.writeBytes(semanticXml)
        entry.writeBytes(replaceJsonHash(originalEntry, "xmlFileSha256", sha256(semanticXml)))
        rejectMutation("XML semantic hash mismatch")
        xml.writeBytes(originalXml)
        entry.writeBytes(originalEntry)

        val artifact =
            Regex("\\\"inputClassArtifacts\\\":\\[\\{\\\"entryCount\\\":\\d+,\\\"kind\\\":\\\"directory\\\",\\\"path\\\":\\\"([^\\\"]+)\\\"")
                .find(entryText)!!.groupValues[1]
        val inputClass = project.projectDir.resolve(artifact).walkTopDown().first { it.isFile && it.extension == "class" }
        val originalInput = inputClass.readBytes()
        inputClass.delete()
        rejectMutation("input class artifact identity mismatch")
        inputClass.writeBytes(originalInput)

        val prepared = Regex("\\\"preparedClassDirectory\\\":\\\"([^\\\"]+)\\\"").find(entryText)!!.groupValues[1]
        val extraClass = project.projectDir.resolve("$prepared/fixture/PostEntryMutation.class")
        extraClass.parentFile.mkdirs()
        extraClass.writeBytes(byteArrayOf(1, 2, 3))
        rejectMutation("physical prepared class inventory differs from manifest")
        extraClass.delete()

        project.projectDir.resolve("stale-head.txt").writeText("move HEAD")
        project.git("add", "stale-head.txt")
        project.git("commit", "-qm", "move fixture head")
        rejectMutation("CLI source commit must equal HEAD")
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
    }

    private fun GradlePluginTestProject.assertRealVerifierMutationBoundary() {
        assertEquals(0, realVerifierBoundary().first)

        val entryText = entryFiles().first { it.readText().contains("\"reportId\":\":sample:jvm|main\"") }.readText()
        val artifact = Regex("\\\"inputClassArtifacts\\\":\\[\\{\\\"entryCount\\\":\\d+,\\\"kind\\\":\\\"directory\\\",\\\"path\\\":\\\"([^\\\"]+)\\\"")
            .find(entryText)!!.groupValues[1]
        val inputClass = projectDir.resolve(artifact).walkTopDown().first { it.isFile && it.extension == "class" }
        val inputBytes = inputClass.readBytes()
        inputClass.delete()
        val removedInput = realVerifierBoundary()
        assertEquals(removedInput.second, 1, removedInput.first)
        assertTrue(removedInput.second.contains("input class artifact identity mismatch"))
        inputClass.writeBytes(inputBytes)

        val prepared = Regex("\\\"preparedClassDirectory\\\":\\\"([^\\\"]+)\\\"")
            .find(entryText)!!.groupValues[1]
        val extraClass = projectDir.resolve("$prepared/fixture/PostEntryMutation.class")
        extraClass.parentFile.mkdirs()
        extraClass.writeBytes(byteArrayOf(1, 2, 3))
        val mutated = realVerifierBoundary()
        assertEquals(mutated.second, 1, mutated.first)
        assertTrue(mutated.second.contains("physical prepared class inventory"))
        extraClass.delete()

        projectDir.resolve("sample/jvm/src/main/kotlin/fixture/JvmLogic.kt").appendText("\n")
        git("add", ".")
        git("commit", "-qm", "move fixture head")
        val stale = realVerifierBoundary()
        assertEquals(stale.second, 1, stale.first)
        assertTrue(stale.second.contains("manifest sourceCommit differs from fixture HEAD"))
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
        fun assertCollision(first: String, second: String) {
            val seen = linkedMapOf<String, String>()
            recordCoverageClassPath(requireNotNull(normalizeCoverageClassPath(first)), seen)
            val error = runCatching {
                recordCoverageClassPath(requireNotNull(normalizeCoverageClassPath(second)), seen)
            }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains("Duplicate or case-colliding coverage class path"))
        }
        // The same normalized path registry receives directory and jar entries.
        assertCollision("fixture/Case.class", "fixture/case.class")
        assertCollision("fixture/Caf\u00e9.class", "fixture/Cafe\u0301.class")

        val project = newProject("prepared-classes").writeCoverageFixture(CoverageFixture(jvmOnly = true))
        project.projectDir.resolve("sample/jvm/build.gradle.kts").appendText(
            """

            tasks.named<com.gasstation.buildlogic.quality.coverage.PrepareCoverageClassesTask>("prepareCoverageMainClasses") {
                inputJars.add(layout.projectDirectory.file("fixture-input.jar"))
                inputDirectories.add(layout.projectDirectory.dir("extra-classes"))
                inputDirectories.add(layout.projectDirectory.dir("extra-classes-two"))
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

        val outside = project.projectDir.parentFile.resolve("outside.class").apply { writeBytes(byteArrayOf(7)) }
        val traversal = project.projectDir.resolve("sample/jvm/extra-classes/fixture/Traversal.class")
        traversal.parentFile.mkdirs()
        Files.createSymbolicLink(traversal.toPath(), outside.toPath())
        failed = project.runner(":sample:jvm:prepareCoverageMainClasses", "--rerun-tasks").buildAndFail()
        assertTrue(failed.output.contains("escapes provider directory"))
        traversal.delete()

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
        val project = newProject("generated-source").writeCoverageFixture(CoverageFixture(jvmOnly = true))
        val outside = project.projectDir.parentFile.resolve("Outside.kt").apply {
            writeText("package fixture\nclass Outside\n")
        }
        val linked = project.projectDir.resolve("sample/jvm/src/main/kotlin/fixture/Outside.kt")
        Files.createSymbolicLink(linked.toPath(), outside.toPath())
        var result = project.runner(
            ":sample:jvm:writeCoverageMainManifestEntry",
            "-Pgasstation.coverageSourceCommit=${project.git("rev-parse", "HEAD").trim()}",
            "--rerun-tasks",
        ).buildAndFail()
        assertTrue(result.output, result.output.contains("Coverage path escapes repository"))
        linked.delete()

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

        result =
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

    private fun GradlePluginTestProject.runProcess(vararg arguments: String): Pair<Int, String> {
        val process = ProcessBuilder(arguments.toList()).directory(projectDir).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        return process.waitFor() to output
    }

    private fun GradlePluginTestProject.installRealVerifierArchitecture() {
        projectDir.resolve("scripts/quality/verify_coverage.py")
            .writeBytes(projectDir.resolve("scripts/quality/real_verify_coverage.py").readBytes())
        projectDir.resolve("config/quality/coverage-baseline.json").delete()
        projectDir.resolve("config/quality/coverage-policy.json").writeText(REAL_JVM_BLOCKING_POLICY)
        projectDir.resolve("sample/jvm/build.gradle.kts").appendText(
            """

            tasks.register<org.gradle.api.tasks.testing.Test>("unrelatedCoverageTest") {
                doFirst { error("unrelated coverage test executed") }
            }
            """.trimIndent(),
        )
        git("add", "-A")
        git("commit", "-qm", "install real verifier architecture")
    }

    private fun replaceJsonHash(original: ByteArray, key: String, digest: String): ByteArray {
        val pattern = Regex("(\\\"$key\\\":\\\")[0-9a-f]{64}(\\\")")
        val text = original.toString(Charsets.UTF_8)
        check(pattern.findAll(text).count() == 1) { "expected one $key in manifest entry" }
        return pattern.replace(text, "${'$'}1$digest${'$'}2").toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun GradlePluginTestProject.realVerifierBoundary(): Pair<Int, String> {
        val process = ProcessBuilder("python3", "scripts/quality/check_real_boundary.py")
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        return process.waitFor() to output
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

    private companion object {
        val REAL_JVM_BLOCKING_POLICY =
            """
            {
              "schemaVersion": 1,
              "enforcementMode": "blocking",
              "activeModules": [":benchmark", ":sample:jvm"],
              "excludedModules": [{"module": ":benchmark", "reason": "connected macrobenchmark and device performance evidence owns this module"}],
              "reports": [{
                "id": ":sample:jvm|main",
                "module": ":sample:jvm",
                "platform": "jvm",
                "variant": "main",
                "testTask": ":sample:jvm:test",
                "sourceRoots": ["sample/jvm/src/main/java", "sample/jvm/src/main/kotlin"],
                "testSourceRoots": ["sample/jvm/src/test/java", "sample/jvm/src/test/kotlin"],
                "ownedSourceRoots": ["sample/jvm/src/main/java", "sample/jvm/src/main/kotlin"]
              }],
              "units": [{
                "id": ":sample:jvm|assembly",
                "family": "assembly",
                "selection": "all",
                "reportIds": [":sample:jvm|main"],
                "sources": []
              }],
              "changedThresholds": {"lineBasisPoints": 8000, "branchBasisPoints": 7000},
              "maximumBaselineDropBasisPoints": 50,
              "maximumFloorRaiseBasisPoints": 200,
              "nonExecutableExceptions": [],
              "unclassifiedAuthoredSource": "fail"
            }
            """.trimIndent()
    }
}
