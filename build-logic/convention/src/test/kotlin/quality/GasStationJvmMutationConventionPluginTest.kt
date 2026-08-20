package com.gasstation.buildlogic.quality

import com.gasstation.buildlogic.quality.mutation.RejectDirectPitestAction
import com.gasstation.buildlogic.quality.mutation.configureSealedInheritedJavaExecDefaults
import com.gasstation.buildlogic.quality.mutation.blockingMutationThreshold
import com.gasstation.buildlogic.quality.mutation.requireSupportedMutationProject
import com.gasstation.buildlogic.quality.mutation.validateBlockingEnforcement
import com.gasstation.buildlogic.quality.coverage.canonicalCoverageJson
import com.gasstation.buildlogic.testing.GradlePluginTestProject
import groovy.json.JsonSlurper
import info.solidsoft.gradle.pitest.validatePitestOptionOverrides
import info.solidsoft.gradle.pitest.canonicalIdentity
import info.solidsoft.gradle.pitest.validateSealedExecutable
import info.solidsoft.gradle.pitest.validateSealedEncodingSurface
import info.solidsoft.gradle.pitest.validateEffectivePitestSurface
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.tasks.JavaExec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

class GasStationJvmMutationConventionPluginTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val sharedGradleUserHome by lazy {
        temporaryFolder.newFolder("mutation-gradle-user-home")
    }

    private val cleanMutationCheckoutRoot by lazy {
        val root = temporaryFolder.newFolder("clean-parent-without-pit-reports")
        val policy = root.resolve("config/quality/mutation-policy.json")
        require(policy.parentFile.mkdirs())
        val repositoryRoot = File(System.getProperty("user.dir")).resolve("../..").canonicalFile
        policy.writeText(repositoryRoot.resolve("config/quality/mutation-policy.json").readText())
        root
    }

    @Test
    fun blockingPhaseUsesExactNativeFloorsAndKeepsSettingsScoreReportOnly() {
        assertEquals(45, blockingMutationThreshold(":domain:station"))
        assertEquals(75, blockingMutationThreshold(":domain:location"))
        assertEquals(null, blockingMutationThreshold(":domain:settings"))

        validateBlockingEnforcement("blocking", "blocking")
        listOf("observe" to "blocking", "blocking" to "observe").forEach { (taskPhase, policyPhase) ->
            val failure = assertThrows(GradleException::class.java) {
                validateBlockingEnforcement(taskPhase, policyPhase)
            }
            assertTrue(failure.message, failure.message.orEmpty().contains("blocking"))
        }
    }

    @Test
    fun sealedInheritedDefaultsExplicitlyOwnUtf8AndDebugSurface() {
        val task = ProjectBuilder.builder().build().tasks.create("sealedJava", JavaExec::class.java)
        task.defaultCharacterEncoding = "UTF-16"
        task.modularity.inferModulePath.set(true)

        configureSealedInheritedJavaExecDefaults(task)

        assertEquals("UTF-8", task.defaultCharacterEncoding)
        assertEquals(1, task.allJvmArgs.count { it == "-Dfile.encoding=UTF-8" })
        assertFalse(task.modularity.inferModulePath.get())
        assertFalse(task.debugOptions.enabled.get())
        assertEquals("localhost", task.debugOptions.host.get())
        assertEquals(5005, task.debugOptions.port.get())
        assertTrue(task.debugOptions.server.get())
        assertTrue(task.debugOptions.suspend.get())
    }

    @Test
    fun encodingSurfaceRejectsSameValueAlternateSourcesAndRequiresOneManagedArgument() {
        validateSealedEncodingSurface(
            defaultCharacterEncoding = "UTF-8",
            explicitJvmArguments = emptyList(),
            mutableSystemProperties = emptyMap<String, String>(),
            effectiveJvmArguments = listOf("-Dfile.encoding=UTF-8", "-Duser.language=en"),
        )

        val mutations: List<() -> Unit> =
            listOf(
            { validateSealedEncodingSurface(null, emptyList(), emptyMap<String, String>(), listOf("-Dfile.encoding=UTF-8")) },
            { validateSealedEncodingSurface("UTF-16", emptyList(), emptyMap<String, String>(), listOf("-Dfile.encoding=UTF-16")) },
            {
                validateSealedEncodingSurface(
                    "UTF-8",
                    listOf("-Dfile.encoding=UTF-8"),
                    emptyMap<String, String>(),
                    listOf("-Dfile.encoding=UTF-8", "-Dfile.encoding=UTF-8"),
                )
            },
            {
                validateSealedEncodingSurface(
                    "UTF-8",
                    emptyList(),
                    mapOf("file.encoding" to "UTF-8"),
                    listOf("-Dfile.encoding=UTF-8"),
                )
            },
            { validateSealedEncodingSurface("UTF-8", emptyList(), emptyMap<String, String>(), emptyList()) },
            {
                validateSealedEncodingSurface(
                    "UTF-8",
                    emptyList(),
                    emptyMap<String, String>(),
                    listOf("-Dfile.encoding=UTF-8", "-Dfile.encoding=UTF-8"),
                )
            },
        )
        mutations.forEachIndexed { index, mutation ->
            val failure = assertThrows("encoding mutation $index", GradleException::class.java, mutation)
            assertTrue(failure.message, failure.message.orEmpty().contains("file.encoding"))
        }
    }

    @Test
    fun executableSurfaceRequiresTheExactLauncherExecutableAndRejectsAbsence() {
        val launcher = File(System.getProperty("java.home"), "bin/java").canonicalFile

        validateSealedExecutable(launcher.absolutePath, launcher)
        listOf(null, File(launcher.parentFile, "alternate-java").absolutePath).forEach { executable ->
            val failure =
                assertThrows(GradleException::class.java) {
                    validateSealedExecutable(executable, launcher)
                }
            assertTrue(failure.message, failure.message.orEmpty().contains("executable/javaLauncher"))
        }
    }

    @Test
    fun exactProjectMappingAndEveryRealPitOptionAliasAreClosedInProcess() {
        listOf(":domain:station", ":domain:location", ":domain:settings")
            .forEach(::requireSupportedMutationProject)
        val failure = assertThrows(GradleException::class.java) {
            requireSupportedMutationProject(":core:network")
        }
        assertTrue(failure.message, failure.message.orEmpty().contains("gasstation.jvm.mutation supports exactly"))

        listOf(
            Triple(listOf("fixture"), null, null) to "--targetTests",
            Triple(null, listOf("+fixture"), null) to "--additionalFeatures",
            Triple(null, null, true) to "--verbose",
        ).forEach { (override, surface) ->
            val optionFailure = assertThrows(GradleException::class.java) {
                validatePitestOptionOverrides(override.first, override.second, override.third)
            }
            assertTrue(optionFailure.message, optionFailure.message.orEmpty().contains(surface))
        }
    }

    @Test
    fun directPitestTypedActionAlwaysRejects() {
        val task = ProjectBuilder.builder().build().tasks.create("pitest")
        val failure = assertThrows(GradleException::class.java) {
            RejectDirectPitestAction(":domain:station:pitestVerified").execute(task)
        }

        assertTrue(
            failure.message,
            failure.message.orEmpty().contains("Direct pitest is unsupported; use :domain:station:pitestVerified"),
        )
    }

    @Test
    fun canonicalEffectiveSurfaceRejectsEveryChangedOrAddedMutationProducingField() {
        val expected = sortedMapOf(
            "pit.mutators" to "DEFAULTS",
            "pit.targetClasses" to "com.gasstation.domain.station.*",
            "pit.sourceDirs" to "repo:domain/station/src/main/kotlin:directory",
            "pit.launchClasspath" to "external:pitest.jar:abc",
            "derivedCli.mutators" to "DEFAULTS",
            "java.argumentProviderCount" to "0",
            "java.environment" to "CI=true",
        )
        validateEffectivePitestSurface(expected, expected)
        expected.keys.forEach { field ->
            val changed = expected.toMutableMap().also { it[field] = "changed" }
            val failure = assertThrows(GradleException::class.java) {
                validateEffectivePitestSurface(expected, changed)
            }
            assertTrue(failure.message, failure.message.orEmpty().contains(field))
        }
        val added = expected + ("unknown.child.option" to "enabled")
        val failure = assertThrows(GradleException::class.java) {
            validateEffectivePitestSurface(expected, added)
        }
        assertTrue(failure.message, failure.message.orEmpty().contains("unknown.child.option"))
    }

    @Test
    fun dedicatedGradleCacheDependenciesUseLocationNeutralContentIdentity() {
        val root = temporaryFolder.newFolder("identity-root")
        val dependency = File(
            root,
            "build/quality/pitest-runtime/gradle-user-home/caches/modules-2/files-2.1/example/dependency.jar",
        )
        dependency.parentFile.mkdirs()
        dependency.writeBytes("stable dependency".toByteArray())

        val identity = canonicalIdentity(dependency, root)

        assertTrue(identity, identity.startsWith("external:dependency.jar:"))
        assertFalse(identity, identity.contains("gradle-user-home"))
    }

    @Test
    fun realSelfContainedTestKitRejectsPitAliasesLateMutationsAndStaleRouteEvidence() {
        assertFalse(cleanMutationCheckoutRoot.resolve("build/reports/pitest/route.json").exists())
        assertFalse(cleanMutationCheckoutRoot.resolve("build/reports/pitest/route-receipt.json").exists())
        val project = mutationProject("real-options")
        assertTrue(File(project.projectDir, "build/reports/pitest/route.json").isFile)
        assertTrue(File(project.projectDir, "build/reports/pitest/route-receipt.json").isFile)
        val cases = listOf(
            listOf("--targetTests", "fixture.TargetTest") to "--targetTests",
            listOf("--additionalFeatures", "+fixture") to "--additionalFeatures",
            listOf("--verbose") to "--verbose",
            listOf("--args=--mutators=EMPTY_RETURNS") to "--args",
            listOf("--debug-jvm") to "debug",
        )
        cases.forEachIndexed { index, (option, diagnostic) ->
            val result = project.runner(
                ":domain:station:pitestVerified",
                "-x", ":domain:station:verifyPitestConfiguration",
                *option.toTypedArray(),
            ).buildAndFail()
            assertTrue("case $index missing $diagnostic: ${result.output}", result.output.contains(diagnostic))
            assertFalse("case $index unexpectedly reached PIT", result.output.contains("PIT >>"))
        }
        project.writeFile(
            "original-collection.init.gradle",
            """
            gradle.beforeProject { candidate ->
                if (candidate.path == ":domain:station") {
                    candidate.afterEvaluate {
                        candidate.tasks.named("pitest").configure { task ->
                            task.additionalClasspath.from(candidate.rootProject.file("README.md"))
                        }
                    }
                }
            }
            """.trimIndent(),
        )
        val originalCollectionInit = File(project.projectDir, "original-collection.init.gradle")
        val originalCollection = project.runner(
            ":domain:station:verifyPitestConfiguration",
            "--init-script", originalCollectionInit.absolutePath,
        ).buildAndFail()
        assertTrue(
            "late original collection mutation passed configuration: ${originalCollection.output}",
            originalCollection.output.contains("pit.additionalClasspath"),
        )

        project.writeFile(
            "environment.init.gradle",
            """
            gradle.beforeProject { candidate ->
                if (candidate.path == ":domain:station") {
                    candidate.afterEvaluate {
                        candidate.tasks.named("pitestVerified").configure { task ->
                            task.environment("SECRET_TOKEN", "must-not-serialize")
                        }
                    }
                }
            }
            """.trimIndent(),
        )
        val environmentInit = File(project.projectDir, "environment.init.gradle")
        val environment = project.runner(
            ":domain:station:verifyPitestConfiguration",
            "--init-script", environmentInit.absolutePath,
        ).buildAndFail()
        assertTrue(
            "late verified environment passed configuration: ${environment.output}",
            environment.output.contains("java.environment"),
        )
        val configuration = File(project.projectDir, "domain/station/build/reports/quality/pitest-configuration.json")
        val configurationText = configuration.takeIf { it.isFile }?.readText().orEmpty()
        assertFalse(
            "configuration evidence serialized an injected secret",
            configurationText.contains("SECRET_TOKEN") || configurationText.contains("must-not-serialize"),
        )
        val lateMutations = listOf(
            """
            gradle.projectsEvaluated {
                tasks.named("pitest") {
                    (this as info.solidsoft.gradle.pitest.PitestTask).additionalClasspath.from(layout.projectDirectory.file("late-original.jar"))
                }
            }
            """.trimIndent() to "original.pit.additionalClasspath",
            """
            tasks.named<JavaExec>("pitestVerified") {
                argumentProviders.add(org.gradle.process.CommandLineArgumentProvider { throw GradleException("PROVIDER_INVOKED") })
            }
            """.trimIndent() to "java.argumentProviderCount",
            """
            tasks.named<JavaExec>("pitestVerified") {
                jvmArgumentProviders.add(org.gradle.process.CommandLineArgumentProvider { throw GradleException("PROVIDER_INVOKED") })
            }
            """.trimIndent() to "java.jvmArgumentProviderCount",
            """
            tasks.named<JavaExec>("pitestVerified") { isIgnoreExitValue = true }
            """.trimIndent() to "java.ignoreExitValue",
            """
            tasks.named<JavaExec>("pitestVerified") { defaultCharacterEncoding = "UTF-16" }
            """.trimIndent() to "java.defaultCharacterEncoding",
            """
            tasks.named<JavaExec>("pitestVerified") { environment("SECRET_TOKEN", "must-not-serialize") }
            """.trimIndent() to "java.environment",
            """
            tasks.named("pitestVerified") {
                (this as info.solidsoft.gradle.pitest.PitestTask).mutators.set(setOf("EMPTY_RETURNS"))
            }
            """.trimIndent() to "pit.mutators",
            """
            gradle.projectsEvaluated {
                tasks.named("pitestVerified") {
                    (this as info.solidsoft.gradle.pitest.PitestTask).sourceDirs.from(layout.projectDirectory.dir("late-source"))
                }
            }
            """.trimIndent() to "pit.sourceDirs",
            """
            gradle.projectsEvaluated {
                tasks.named("pitestVerified") {
                    (this as info.solidsoft.gradle.pitest.PitestTask).mutableCodePaths.from(layout.projectDirectory.dir("late-mutable"))
                }
            }
            """.trimIndent() to "pit.mutableCodePaths",
            """
            gradle.projectsEvaluated {
                tasks.named("pitestVerified") {
                    (this as info.solidsoft.gradle.pitest.PitestTask).additionalClasspath.from(layout.projectDirectory.file("late-additional.jar"))
                }
            }
            """.trimIndent() to "pit.additionalClasspath",
            """
            gradle.projectsEvaluated {
                tasks.named("pitestVerified") {
                    (this as info.solidsoft.gradle.pitest.PitestTask).launchClasspath.from(layout.projectDirectory.file("late-launch.jar"))
                }
            }
            """.trimIndent() to "execution surface: classpath",
        )
        lateMutations.forEachIndexed { index, (mutation, diagnostic) ->
            project.writeFile("domain/station/build.gradle.kts", mutationBuildScript(mutation))
            val result = project.runner(
                ":domain:station:pitestVerified",
                "-x", ":domain:station:verifyPitestConfiguration",
            ).buildAndFail()
            assertTrue("late case $index missing $diagnostic: ${result.output}", result.output.contains(diagnostic))
            assertFalse("late case $index invoked a provider", result.output.contains("PROVIDER_INVOKED"))
            assertFalse("late case $index serialized an injected value", result.output.contains("must-not-serialize"))
        }
        project.writeFile("domain/station/build.gradle.kts", mutationBuildScript(""))
        val nonZero = project.runner(
            ":domain:station:pitestVerified",
            "-x", ":domain:station:verifyPitestConfiguration",
        ).buildAndFail()
        assertTrue("canonical non-zero PIT launch was unexpectedly ignored: ${nonZero.output}", nonZero.output.contains("PIT"))
        assertFalse("canonical non-zero PIT launch reported success", nonZero.output.contains("BUILD SUCCESSFUL"))

        val route = File(project.projectDir, "build/reports/pitest/route.json")
        val current = route.readText()
        val stale = current.replace("\"status\":\"selected\"", "\"status\":\"not-applicable\"")
        assertFalse("fixture route mutation did not change bytes", current == stale)
        route.writeText(stale)
        val failure = project.runner(":domain:station:verifyPitestConfiguration").buildAndFail()
        assertTrue(
            failure.output,
            failure.output.contains("PIT route receipt predecessor identity differs"),
        )

        val policy = File(project.projectDir, "config/quality/mutation-policy.json")
        writeCanonicalRouteEvidence(project, policy.readText())
        val receipt = File(project.projectDir, "build/reports/pitest/route-receipt.json")
        val receiptText = receipt.readText()
        val mismatchedReceipt = receiptText.replace(
            "\"policy\":\"${sha256(policy.readBytes())}\"",
            "\"policy\":\"${"0".repeat(64)}\"",
        )
        assertFalse("fixture receipt mutation did not change bytes", receiptText == mismatchedReceipt)
        receipt.writeText(mismatchedReceipt)
        val receiptFailure = project.runner(":domain:station:verifyPitestConfiguration").buildAndFail()
        assertTrue(
            receiptFailure.output,
            receiptFailure.output.contains("PIT route receipt predecessor identity differs"),
        )
    }

    private fun mutationProject(name: String): GradlePluginTestProject {
        val project = GradlePluginTestProject.create(temporaryFolder.newFolder(name), sharedGradleUserHome)
        project.writeSettings(
            """
            rootProject.name = "mutation-fixture"
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            include(":domain:station", ":domain:location", ":domain:settings")
            """.trimIndent(),
        )
        project.writeFile(
            "gradle/libs.versions.toml",
            """
            [versions]
            kotlin = "2.4.10"
            [libraries]
            kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
            """.trimIndent(),
        )
        val policyText =
            cleanMutationCheckoutRoot
                .resolve("config/quality/mutation-policy.json")
                .canonicalFile
                .readText()
        project.writeFile("config/quality/mutation-policy.json", policyText)
        writeCanonicalRouteEvidence(project, policyText)
        listOf("station", "location", "settings").forEach { module ->
            project.writeFile(
                "domain/$module/build.gradle.kts",
                mutationBuildScript(),
            )
            project.writeFile(
                "domain/$module/src/main/kotlin/com/gasstation/domain/$module/Fixture.kt",
                "package com.gasstation.domain.$module\npublic class Fixture { public fun answer(): Int = 1 }",
            )
        }
        return project
    }

    private fun writeCanonicalRouteEvidence(
        project: GradlePluginTestProject,
        policyText: String,
    ) {
        val policyBytes = (policyText.trimEnd('\r', '\n') + "\n").toByteArray(UTF_8)
        @Suppress("UNCHECKED_CAST")
        val policy = JsonSlurper().parse(policyBytes) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val modules = policy.getValue("modules") as Map<String, Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val pitest = policy.getValue("pitest") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val executionEnvironment = policy.getValue("executionEnvironmentPolicy") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val gitObjectViewPolicy = policy.getValue("gitObjectViewPolicy") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val profiles = policy.getValue("bootstrapProfiles") as Map<String, Map<String, Any?>>
        val profileName = "darwin-arm64"
        val profile = profiles.getValue(profileName)
        val digest = "1".repeat(64)
        val observedTools =
            listOf("bash", "env", "git", "python").associateWith { name ->
                sortedMapOf<String, Any?>(
                    "entryPath" to "/fixture/$name",
                    "entryType" to "regular",
                    "fileType" to "regular",
                    "mode" to "0755",
                    "resolvedPath" to "/fixture/$name",
                    "sha256" to digest,
                    "versionSha256" to digest,
                )
            }.toSortedMap()
        val java =
            sortedMapOf<String, Any?>(
                "executableSha256" to digest,
                "major" to 21,
                "runtimeVersion" to "21.0.0-test",
                "toolchainRole" to "mutation-runtime",
                "vendorFamily" to "Eclipse Adoptium/Temurin",
            )
        val bootstrap =
            sortedMapOf<String, Any?>(
                "environmentPolicy" to executionEnvironment.getValue("policyVersion"),
                "gitObjectView" to
                    sortedMapOf<String, Any?>(
                        "inventorySha256" to digest,
                        "policy" to gitObjectViewPolicy.getValue("policyVersion"),
                        "prefixSha256" to digest,
                    ),
                "imageIdentity" to null,
                "java" to java,
                "observedToolBundleSha256" to sha256(canonicalCoverageJson(observedTools)),
                "observedTools" to observedTools,
                "profile" to profileName,
                "profileSha256" to sha256(canonicalCoverageJson(profile)),
            )
        val hostNeutral =
            sortedMapOf<String, Any?>(
                "java" to
                    sortedMapOf<String, Any?>(
                        "major" to 21,
                        "toolchainRole" to "mutation-runtime",
                        "vendorFamily" to "Eclipse Adoptium/Temurin",
                    ),
                "pitestEngine" to pitest.getValue("pitestVersion"),
                "pitestPlugin" to pitest.getValue("pluginVersion"),
                "reportGeneration" to pitest,
                "schema" to "host-neutral-mutation-identity-v1",
                "targets" to
                    modules.toSortedMap().mapValues { (_, module) ->
                        sortedMapOf<String, Any?>(
                            "sourceSets" to listOf("main", "test"),
                            "targetClasses" to module.getValue("targetClasses"),
                            "targetTests" to module.getValue("targetTests"),
                        )
                    },
            )
        val perRun =
            sortedMapOf<String, Any?>(
                "imageIdentity" to null,
                "javaExecutableSha256" to digest,
                "javaRuntimeVersion" to "21.0.0-test",
                "observedToolBundleSha256" to bootstrap.getValue("observedToolBundleSha256"),
                "profileDefinitionSha256" to bootstrap.getValue("profileSha256"),
                "schema" to "per-run-execution-provenance-route-v1",
                "selectedProfile" to profileName,
            )
        val selectedModules = modules.keys.sorted()
        val selectedTasks = selectedModules.map { modules.getValue(it).getValue("pitestTask") }
        val sourceCommit = "1".repeat(40)
        val route =
            sortedMapOf<String, Any?>(
                "baseCommit" to null,
                "bootstrap" to bootstrap,
                "changes" to emptyList<Any>(),
                "environmentPolicy" to executionEnvironment.getValue("policyVersion"),
                "event" to "local-all",
                "gitObjectViewPolicy" to gitObjectViewPolicy.getValue("policyVersion"),
                "hostNeutralMutationIdentity" to hostNeutral,
                "hostNeutralMutationIdentitySha256" to sha256(canonicalCoverageJson(hostNeutral)),
                "mergeBase" to null,
                "perRunExecutionProvenance" to perRun,
                "perRunExecutionProvenanceSha256" to sha256(canonicalCoverageJson(perRun)),
                "policySha256" to sha256(policyBytes),
                "schemaVersion" to 1,
                "selectedModules" to selectedModules,
                "selectedTasks" to selectedTasks,
                "sourceCommit" to sourceCommit,
                "status" to "selected",
            )
        val routeBytes = canonicalCoverageJson(route) + byteArrayOf('\n'.code.toByte())
        val tasksText = selectedTasks.joinToString(separator = "\n", postfix = "\n")
        val tasksBytes = tasksText.toByteArray(UTF_8)
        val receipt =
            sortedMapOf<String, Any?>(
                "bootstrap" to bootstrap,
                "predecessors" to
                    sortedMapOf<String, Any?>(
                        "policy" to sha256(policyBytes),
                        "route" to sha256(routeBytes),
                        "tasks" to sha256(tasksBytes),
                    ),
                "schema" to "pitest-route-receipt-v1",
                "sourceCommit" to sourceCommit,
                "status" to "selected",
            )
        project.writeFile("build/reports/pitest/route.json", routeBytes.toString(UTF_8))
        project.writeFile("build/reports/pitest/tasks.txt", tasksText)
        project.writeFile(
            "build/reports/pitest/route-receipt.json",
            canonicalCoverageJson(receipt).toString(UTF_8),
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun mutationBuildScript(extra: String = ""): String =
        """
        import org.gradle.api.tasks.JavaExec
        plugins { id("gasstation.jvm.mutation") }
        dependencies { testImplementation(kotlin("test")) }
        $extra
        """.trimIndent()
}
