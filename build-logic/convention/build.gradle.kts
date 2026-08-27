import groovy.json.JsonSlurper
import java.nio.file.Files
import java.nio.file.Path as NioPath
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.TestResult

plugins {
    `kotlin-dsl`
}

val testKitAapt2Version = "9.3.2-15703166"
val testKitAapt2Artifacts =
    mapOf(
        "aapt2-$testKitAapt2Version-linux.jar" to
            "e772a3dae8354764f1b0793903218427f483982445207f2e4ffc8c2026755bd4",
        "aapt2-$testKitAapt2Version-osx.jar" to
            "1e35bc2ce18c3aae840be2a29659ce50d6043e907a44d98ee1cf375d044fa29c",
        "aapt2-$testKitAapt2Version.pom" to
            "96be995aec595ca9d9fc3ae347ea0f22575e7f01f1e5212018a453d9c86e64a3",
    )

fun NioPath.sha256(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(Files.readAllBytes(this))
        .joinToString("") { byte -> "%02x".format(byte) }

fun deleteClosedTree(root: NioPath) {
    if (!Files.exists(root)) return
    check(Files.isDirectory(root) && !Files.isSymbolicLink(root)) {
        "TestKit read-only dependency-cache output is unsafe: $root"
    }
    val paths = Files.walk(root).use { stream -> stream.toList() }
    paths.forEach { path ->
        check(!Files.isSymbolicLink(path)) {
            "TestKit read-only dependency-cache output contains a symbolic link: $path"
        }
    }
    paths.asReversed().forEach(Files::delete)
}

fun copyClosedCacheTree(
    source: NioPath,
    destination: NioPath,
) {
    check(Files.isDirectory(source) && !Files.isSymbolicLink(source)) {
        "TestKit dependency-cache seed source is missing or unsafe: $source"
    }
    Files.walk(source).use { paths ->
        paths.sorted().forEach { path ->
            check(!Files.isSymbolicLink(path)) {
                "TestKit dependency-cache seed source contains a symbolic link: $path"
            }
            val relative = source.relativize(path)
            val name = path.fileName?.toString().orEmpty()
            if (name.endsWith(".lock") || name == "gc.properties") return@forEach
            val target = destination.resolve(relative)
            if (Files.isDirectory(path)) {
                Files.createDirectories(target)
            } else {
                check(Files.isRegularFile(path)) {
                    "TestKit dependency-cache seed source entry is not a regular file: $path"
                }
                Files.createDirectories(target.parent)
                Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES)
            }
        }
    }
}

fun seedInventory(modulesRoot: NioPath): String {
    check(Files.isDirectory(modulesRoot) && !Files.isSymbolicLink(modulesRoot)) {
        "TestKit read-only dependency cache is missing or unsafe: $modulesRoot"
    }
    val inventoryPaths =
        Files.walk(modulesRoot).use { paths -> paths.sorted().toList() }
    inventoryPaths.forEach { path ->
        check(!Files.isSymbolicLink(path)) {
            "TestKit read-only dependency cache contains a symbolic link: $path"
        }
    }
    val rows =
        inventoryPaths
            .asSequence()
            .filter { path -> Files.isRegularFile(path) }
            .map { path ->
                val relative = modulesRoot.relativize(path).toString().replace(File.separatorChar, '/')
                check(!relative.endsWith(".lock") && !relative.endsWith("/gc.properties")) {
                    "TestKit read-only dependency cache contains mutable Gradle state: $relative"
                }
                "$relative\t${path.sha256()}\t${Files.size(path)}"
            }.toList()
    check(rows.isNotEmpty()) { "TestKit read-only dependency cache is empty" }
    return rows.joinToString(separator = "\n", postfix = "\n")
}

fun requireExactAapt2Artifacts(artifactRoot: NioPath) {
    check(Files.isDirectory(artifactRoot) && !Files.isSymbolicLink(artifactRoot)) {
        "Exact TestKit AAPT2 cache entry is missing or unsafe: $artifactRoot"
    }
    val artifactPaths = Files.walk(artifactRoot).use { paths -> paths.toList() }
    artifactPaths.forEach { path ->
        check(!Files.isSymbolicLink(path)) { "Exact TestKit AAPT2 cache entry is a symbolic link: $path" }
    }
    val artifacts = artifactPaths.filter { path -> Files.isRegularFile(path) }
    check(artifacts.size == testKitAapt2Artifacts.size) {
        "Exact TestKit AAPT2 cache artifact count differs"
    }
    check(artifacts.map { path -> path.fileName.toString() }.toSet() == testKitAapt2Artifacts.keys) {
        "Exact TestKit AAPT2 cache artifact inventory differs"
    }
    artifacts.forEach { artifact ->
        val expected = testKitAapt2Artifacts.getValue(artifact.fileName.toString())
        check(artifact.sha256() == expected) {
            "Exact TestKit AAPT2 cache artifact checksum differs: ${artifact.fileName}"
        }
    }
}

group = "com.gasstation.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(libs.findLibrary("android-gradlePlugin").get())
    implementation(libs.findLibrary("kotlin-gradlePlugin").get())
    implementation(libs.findLibrary("kotlin-compose-gradlePlugin").get())
    implementation(libs.findLibrary("ksp-gradlePlugin").get())
    implementation(libs.findLibrary("hilt-gradlePlugin").get())
    implementation(libs.findLibrary("spotless-gradlePlugin").get())
    implementation(libs.findLibrary("roborazzi-gradlePlugin").get())
    implementation("org.jacoco:org.jacoco.core:0.8.15")
    implementation(libs.findLibrary("asm").get())
    implementation(libs.findLibrary("pitest-gradlePlugin").get())
    testImplementation(gradleTestKit())
    testImplementation(libs.findLibrary("junit").get())
}

// TestKit projects intentionally exercise dependency versions which are not selected by the
// production graph. Keep each seed isolated so Gradle conflict resolution cannot hide an older
// fixture version from dependency-verification metadata generation.
val testKitVerificationSeeds =
    listOf(
        "com.google.android:android:4.1.1.4",
        "androidx.annotation:annotation-experimental:1.6.0",
        "androidx.annotation:annotation-jvm:1.7.0",
        "androidx.core:core-ktx:1.19.0",
        "com.google.guava:guava:33.7.1-jre",
        "com.google.guava:guava:33.7.1-jre",
        "org.junit:junit-bom:6.1.3",
        "org.junit:junit-bom:6.1.3",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.11.0",
        "com.android.tools.build:aapt2:$testKitAapt2Version:linux",
        "com.android.tools.build:aapt2:$testKitAapt2Version:osx",
    ).mapIndexed { index, coordinate ->
        configurations.create("testKitVerificationSeed$index") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }.also { configuration -> dependencies.add(configuration.name, coordinate) }
    }

val captureTestKitDependencyVerificationMetadata =
    tasks.register("captureTestKitDependencyVerificationMetadata") {
        group = "verification"
        description = "Captures the reviewed TestKit-only dependency graph for checksum generation."
        notCompatibleWithConfigurationCache("TestKit dependency capture must resolve a fresh reviewed graph")
        doLast {
            testKitVerificationSeeds.forEach { configuration -> configuration.files.forEach(File::getName) }
        }
    }

val testKitReadOnlyDependencyCache =
    layout.buildDirectory.dir("testkit-read-only-dependency-cache")

val prepareTestKitReadOnlyDependencyCache =
    tasks.register("prepareTestKitReadOnlyDependencyCache") {
        group = "verification"
        description = "Copies the exact AAPT2 cache into a TestKit-safe shared read-only dependency seed."
        notCompatibleWithConfigurationCache("TestKit read-only dependency-cache preparation is a fresh seed")
        dependsOn(captureTestKitDependencyVerificationMetadata)
        outputs.dir(testKitReadOnlyDependencyCache)
        outputs.upToDateWhen { false }
        doLast {
            val sourceModules = gradle.gradleUserHomeDir.toPath().resolve("caches/modules-2")
            val sourceAapt2 =
                sourceModules.resolve(
                    "files-2.1/com.android.tools.build/aapt2/$testKitAapt2Version",
                )
            requireExactAapt2Artifacts(sourceAapt2)

            val metadataCandidates =
                Files.list(sourceModules).use { paths ->
                    paths
                        .filter { path ->
                            Files.isDirectory(path) &&
                                path.fileName.toString().startsWith("metadata-") &&
                                Files.isRegularFile(
                                    path.resolve(
                                        "descriptors/com.android.tools.build/aapt2/$testKitAapt2Version/" +
                                            "d4e342018b23d58be902a60e67105aa1/descriptor.bin",
                                    ),
                                )
                        }.toList()
                }
            check(metadataCandidates.size == 1) {
                "Exact TestKit AAPT2 Gradle metadata-cache generation differs: $metadataCandidates"
            }

            val seedRoot = testKitReadOnlyDependencyCache.get().asFile.toPath()
            deleteClosedTree(seedRoot)
            val destinationModules = seedRoot.resolve("modules-2")
            copyClosedCacheTree(
                metadataCandidates.single(),
                destinationModules.resolve(metadataCandidates.single().fileName),
            )
            copyClosedCacheTree(
                sourceAapt2,
                destinationModules.resolve(
                    "files-2.1/com.android.tools.build/aapt2/$testKitAapt2Version",
                ),
            )
            val manifest = seedInventory(destinationModules)
            Files.writeString(seedRoot.resolve("seed-manifest.tsv"), manifest, Charsets.UTF_8)
        }
    }

val verifyTestKitReadOnlyDependencyCache =
    tasks.register("verifyTestKitReadOnlyDependencyCache") {
        group = "verification"
        description = "Rehashes the immutable TestKit AAPT2 dependency seed after nested builds."
        notCompatibleWithConfigurationCache("TestKit read-only dependency-cache verification rehashes live files")
        doLast {
            val seedRoot = testKitReadOnlyDependencyCache.get().asFile.toPath()
            val manifest = seedRoot.resolve("seed-manifest.tsv")
            check(Files.isRegularFile(manifest) && !Files.isSymbolicLink(manifest)) {
                "TestKit read-only dependency-cache manifest is missing or unsafe"
            }
            check(Files.readString(manifest, Charsets.UTF_8) == seedInventory(seedRoot.resolve("modules-2"))) {
                "TestKit read-only dependency cache changed during nested builds"
            }
            requireExactAapt2Artifacts(
                seedRoot.resolve(
                    "modules-2/files-2.1/com.android.tools.build/aapt2/$testKitAapt2Version",
                ),
            )
        }
    }

val finalizeTask9TestKitFailureEvidence =
    tasks.register("finalizeTask9TestKitFailureEvidence") {
        val workerTracePath = providers.environmentVariable("GASSTATION_TESTKIT_WORKER_TRACE")
        val failureOutputPath = providers.environmentVariable("GASSTATION_TESTKIT_FAILURE_OUTPUT")
        onlyIf { workerTracePath.isPresent && failureOutputPath.isPresent }
        doLast {
            val workerTrace = file(workerTracePath.get()).toPath()
            val liveStage = file(failureOutputPath.get()).toPath()
            check(Files.isDirectory(liveStage) && !Files.isSymbolicLink(liveStage)) {
                "TestKit live failure stage is missing or unsafe"
            }
            check(Files.isRegularFile(workerTrace) && !Files.isSymbolicLink(workerTrace)) {
                "TestKit worker trace is missing or unsafe"
            }
            val stagedWorker = liveStage.resolve("worker-events.tsv")
            Files.copy(workerTrace, stagedWorker)
            val artifacts =
                Files.list(liveStage).use { paths ->
                    paths
                        .filter { path -> path.fileName.toString() != "live-stage-manifest.json" }
                        .sorted()
                        .map { path ->
                            check(Files.isRegularFile(path) && !Files.isSymbolicLink(path)) {
                                "TestKit live failure artifact is unsafe"
                            }
                            val name = path.fileName.toString()
                            check(name == "worker-events.tsv" || Regex("TEST-[0-9a-f]{64}\\.xml").matches(name)) {
                                "TestKit live failure artifact identity differs"
                            }
                            val body = Files.readAllBytes(path)
                            val sha =
                                MessageDigest
                                    .getInstance("SHA-256")
                                    .digest(body)
                                    .joinToString("") { byte -> "%02x".format(byte) }
                            "{\"path\":\"$name\",\"sha256\":\"$sha\",\"size\":${body.size}}"
                        }.toList()
                }
            check(artifacts.any { row -> row.contains("TEST-") } && artifacts.any { row -> row.contains("worker-events.tsv") }) {
                "TestKit live failure inventory is incomplete"
            }
            val manifest =
                "{\"artifacts\":[${artifacts.joinToString(",")}],\"schemaVersion\":1,\"status\":\"SEALED\"}\n"
            val temporary = liveStage.resolve(".live-stage-manifest.json.tmp")
            Files.writeString(temporary, manifest, Charsets.UTF_8)
            Files.move(
                temporary,
                liveStage.resolve("live-stage-manifest.json"),
                StandardCopyOption.ATOMIC_MOVE,
            )
        }
    }

val task9OrderedDispatchSha256 = "94346faebdd4989670c3518513cf0998bcf871c6775d2c8d71687a1200692930"
val task9OrderedLanesSha256 = "763bf9c30b2582b8b09a1ee4b5ce25a6234baf8c10d49238083a1e7c56015bd3"
// task9-ordered-owners:start
val task9OrderedTestOwners =
    listOf(
        "com.gasstation.buildlogic.RoborazziPropertySelectionTest",
        "com.gasstation.buildlogic.quality.coverage.CoverageProviderTopologyTest",
        "com.gasstation.buildlogic.RoborazziLifecycleSelectionTest",
        "com.gasstation.buildlogic.AndroidLintBaselineIsolationTest",
        "com.gasstation.buildlogic.RoborazziAggregateLifecycleTest",
        "com.gasstation.buildlogic.quality.coverage.CoverageCacheBehaviorTest",
        "com.gasstation.buildlogic.AndroidLintPropertySelectionTest",
        "com.gasstation.buildlogic.AndroidLintReportRegenerationTest",
        "com.gasstation.buildlogic.KotlinCompilerJvmConventionTest",
        "com.gasstation.buildlogic.KotlinCompilerJvmWarningPolicyTest",
        "com.gasstation.buildlogic.AndroidLintWarningPromotionTest",
        "com.gasstation.buildlogic.quality.coverage.CoverageAndroidExecutionDataTest",
        "com.gasstation.buildlogic.GradlePluginHarnessEnvironmentRejectionTest",
        "com.gasstation.buildlogic.KotlinCompilerAndroidCacheTest",
        "com.gasstation.buildlogic.quality.coverage.CoverageModuleOwnershipTest",
        "com.gasstation.buildlogic.quality.GasStationJvmMutationConventionPluginTest",
        "com.gasstation.buildlogic.GradlePluginHarnessEnvironmentSuccessTest",
        "com.gasstation.buildlogic.quality.RootQualityConfigurationCacheTest",
        "com.gasstation.buildlogic.quality.ProductionDependencyBoundaryTest",
        "com.gasstation.buildlogic.RoborazziConfigurationCacheTest",
        "com.gasstation.buildlogic.KotlinCompilerStrictModulePolicyTest",
        "com.gasstation.buildlogic.quality.coverage.CoveragePreparedClassesTest",
        "com.gasstation.buildlogic.quality.RootQualityAbiUpdaterTest",
        "com.gasstation.buildlogic.KotlinCompilerAndroidConventionTest",
        "com.gasstation.buildlogic.GradlePluginHarnessFailureAssertionsTest",
        "com.gasstation.buildlogic.quality.RootQualityFixedPolicyTest",
        "com.gasstation.buildlogic.quality.RootQualityRelocationTest",
        "com.gasstation.buildlogic.quality.coverage.CoverageReportMutationTest",
        "com.gasstation.buildlogic.AndroidLintManagedDevicesTest",
        "com.gasstation.buildlogic.quality.coverage.CoverageVerifierMutationTest",
        "com.gasstation.buildlogic.quality.RootQualityTaskSurfaceTest",
        "com.gasstation.buildlogic.quality.coverage.CoverageGeneratedSourceTest",
        "com.gasstation.buildlogic.quality.RootQualityModuleBoundaryTest",
        "com.gasstation.buildlogic.quality.RootQualityRuntimeIdentityTest",
        "com.gasstation.buildlogic.KotlinCompilerRunnerPolicyTest",
        "com.gasstation.buildlogic.quality.RootQualityComposeSafeTest",
        "com.gasstation.buildlogic.RoborazziPropertyValidationTest",
        "com.gasstation.buildlogic.quality.RootQualityComposeForbiddenTest",
        "com.gasstation.buildlogic.quality.coverage.CoverageSemanticIdentityTest",
        "com.gasstation.buildlogic.ContractApiConventionTest",
        "com.gasstation.buildlogic.quality.RootQualityComposeDiagnosticsTest",
        "com.gasstation.buildlogic.GradlePluginHarnessRunnerPolicyTest",
        "com.gasstation.buildlogic.quality.RootQualityRuntimeRejectionTest",
        "GasStationConventionPropertiesTest",
        "com.gasstation.buildlogic.GradlePluginHarnessFileSafetyTest",
        "com.gasstation.buildlogic.quality.RootQualityRootApplicationTest",
        "com.gasstation.buildlogic.quality.VerifyPublicApiBoundariesTaskTest",
        "com.gasstation.buildlogic.quality.coverage.CoveragePackageLexerTest",
        "com.gasstation.buildlogic.GradlePluginHarnessIsolationTest",
        "com.gasstation.buildlogic.quality.coverage.CoverageExecutionMergeTest",
        "com.gasstation.buildlogic.quality.KotlinAbiDumpParserTest",
        "com.gasstation.buildlogic.quality.ProductionDependencyPolicyTest",
    )
// task9-ordered-owners:end
val task9OrderedLaneOwners =
    (0 until 5).map { laneOffset ->
        task9OrderedTestOwners.filterIndexed { index, _ -> index % 5 == laneOffset }
    }
val task9OrderedTestClassRoots =
    task9OrderedTestOwners.indices.map { index ->
        layout.buildDirectory.dir("task9-ordered-test-classes/" + (index + 1).toString().padStart(6, '0'))
    }
val task9OrderedBinaryResults = layout.buildDirectory.dir("task9-ordered-test-results/binary")
val prepareTask9OrderedTestDispatch =
    tasks.register("prepareTask9OrderedTestDispatch") {
        dependsOn(tasks.named("compileTestKotlin"))
        inputs.dir(layout.buildDirectory.dir("classes/kotlin/test"))
        outputs.dirs(task9OrderedTestClassRoots)
        outputs.upToDateWhen { false }
        doLast {
            val stagingRoot = layout.buildDirectory.dir("task9-ordered-test-classes").get().asFile.toPath()
            deleteClosedTree(stagingRoot)
            val compiledRoot = layout.buildDirectory.dir("classes/kotlin/test").get().asFile.toPath()
            task9OrderedTestOwners.forEachIndexed { index, owner ->
                val relative = owner.replace('.', '/') + ".class"
                val source = compiledRoot.resolve(relative)
                check(Files.isRegularFile(source) && !Files.isSymbolicLink(source)) {
                    "Task-9 ordered dispatch owner class is missing or unsafe: $owner"
                }
                val destinationRoot = task9OrderedTestClassRoots[index].get().asFile.toPath()
                val destination = destinationRoot.resolve(relative)
                Files.createDirectories(destination.parent)
                Files.copy(source, destination)
            }
        }
    }

tasks.withType<Test>().configureEach {
    dependsOn(prepareTask9OrderedTestDispatch)
    dependsOn(prepareTestKitReadOnlyDependencyCache)
    finalizedBy(verifyTestKitReadOnlyDependencyCache)
    testClassesDirs = files(task9OrderedTestClassRoots)
    binaryResultsDirectory.set(task9OrderedBinaryResults)
    doFirst {
        val binaryRoot = task9OrderedBinaryResults.get().asFile.toPath()
        deleteClosedTree(binaryRoot)
        check(!Files.exists(binaryRoot)) { "Task-9 ordered dispatch prior binary results remain" }
    }
    environment(
        "GRADLE_RO_DEP_CACHE",
        testKitReadOnlyDependencyCache.get().asFile.absolutePath,
    )
    val outerTimeoutProperty = providers.gradleProperty("gasstation.task9LocalLinuxConventionTestTimeoutMinutes").orNull
    val outerTimeoutMarker = providers.environmentVariable("GASSTATION_TASK9_LOCAL_LINUX_OWNERSHIP_MARKER").orNull
    require((outerTimeoutProperty == null) == (outerTimeoutMarker == null)) {
        "Task-9 local Linux outer timeout property and ownership marker must be configured together"
    }
    var timeoutMinutes = 15L
    if (outerTimeoutProperty != null) {
        require(outerTimeoutProperty == "35") { "Task-9 local Linux outer timeout must be exact 35" }
        require(
            outerTimeoutMarker == "/evidence-work/task9-local-linux-ownership-marker.json" &&
                name == "test" && path == ":convention:test" &&
                project.name == "convention" && project.path == ":convention" &&
                project.rootProject.name == "build-logic" && project.gradle.buildPath == ":build-logic" &&
                project.gradle.buildPath + path == ":build-logic:convention:test" &&
                project.gradle.parent != null,
        ) {
            "Task-9 local Linux outer timeout task or marker path differs"
        }
        val markerPath = file(outerTimeoutMarker).toPath()
        val governedFailureOutput = providers.environmentVariable("GASSTATION_TESTKIT_FAILURE_OUTPUT").orNull
        require(Files.isRegularFile(markerPath) && !Files.isSymbolicLink(markerPath)) {
            "Task-9 local Linux outer timeout marker is missing or unsafe"
        }
        require(
            Files.getPosixFilePermissions(markerPath) ==
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        ) {
            "Task-9 local Linux outer timeout marker must be exact mode 0600"
        }
        val markerBytes = Files.readString(markerPath, Charsets.UTF_8)
        val marker = JsonSlurper().parseText(markerBytes) as? Map<*, *>
            ?: throw GradleException("Task-9 local Linux outer timeout marker is malformed")
        val keys =
            listOf(
                "attemptId", "container", "context", "dispatchSha256", "governedCommand",
                "lanesSha256", "methodLedgerSha256", "outerConventionTestTimeoutMinutes", "ownerLedgerSha256", "ownershipMarkerSha256",
                "policySha256", "profile", "schemaVersion", "sourceCommit", "taskId", "taskPath",
            )
        require(marker.keys == keys.toSet()) { "Task-9 local Linux outer timeout marker fields differ" }
        require(
            marker["outerConventionTestTimeoutMinutes"] == 35 &&
                marker["methodLedgerSha256"] == "11f019e4ab2f034a6fd3ab27302b5917bb50051bbe365cafb9d76b8bb2cca38b" &&
                marker["ownerLedgerSha256"] == "6e3d0fa1d2c5ecc4824595f989d092161e8225ad9ed9b6d386e262073e50e5ac" &&
                marker["lanesSha256"] == task9OrderedLanesSha256 &&
                marker["dispatchSha256"] == task9OrderedDispatchSha256 &&
                marker["schemaVersion"] == 1 &&
                marker["taskId"] == "quality-task-9-local-linux-evidence" &&
                marker["taskPath"] == ":build-logic:convention:test" &&
                marker["profile"] == "gasstation-task9-linux-amd64" &&
                marker["context"] == "colima-gasstation-task9-linux-amd64" &&
                marker["container"] == "gasstation-task9-evidence" &&
                governedFailureOutput != null &&
                file(governedFailureOutput).parentFile.path == "/evidence-work/testkit-failures" &&
                marker["governedCommand"] == file(governedFailureOutput).name &&
                marker["governedCommand"] in setOf("metadata-capture-1", "metadata-capture-2") &&
                Regex("attempt-[0-9]{6}").matches(marker["attemptId"].toString()) &&
                Regex("[0-9a-f]{40}").matches(marker["sourceCommit"].toString()) &&
                Regex("[0-9a-f]{64}").matches(marker["policySha256"].toString()) &&
                Regex("[0-9a-f]{64}").matches(marker["ownershipMarkerSha256"].toString()),
        ) {
            "Task-9 local Linux outer timeout marker identity differs"
        }
        fun jsonValue(value: Any?): String =
            when (value) {
                is Number -> value.toString()
                is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                else -> throw GradleException("Task-9 local Linux outer timeout marker value type differs")
            }
        val canonical =
            keys.joinToString(separator = ",", prefix = "{", postfix = "}\n") { key ->
                "\"$key\":${jsonValue(marker[key])}"
            }
        require(markerBytes == canonical) { "Task-9 local Linux outer timeout marker is noncanonical" }
        timeoutMinutes = 35L
    }
    timeout.set(Duration.ofMinutes(timeoutMinutes))
    maxParallelForks = 5
    systemProperty("gasstation.convention.test.maxParallelForks", maxParallelForks)
    systemProperty("gasstation.convention.test.dispatchSha256", task9OrderedDispatchSha256)
    systemProperty("gasstation.convention.test.lanesSha256", task9OrderedLanesSha256)

    val task9ObservedWorkers = mutableMapOf<String, Int>()
    val task9ObservedOwnerSequences = mutableMapOf<Int, MutableList<String>>()
    val task9ObservationLock = Any()
    fun task9WorkerNumber(descriptor: TestDescriptor): Int? {
        var current = descriptor.parent
        while (current != null) {
            val match = Regex("Gradle Test Executor ([1-9][0-9]*)").matchEntire(current.name)
            if (match != null) return match.groupValues[1].toInt()
            current = current.parent
        }
        return null
    }
    addTestListener(
        object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) = Unit

            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent != null) return
                synchronized(task9ObservationLock) {
                    val workerNumbers = task9ObservedWorkers.values.toSortedSet().toList()
                    if (task9ObservedWorkers.keys != task9OrderedTestOwners.toSet() || workerNumbers.size != 5) {
                        logger.warn("Task-9 ordered dispatch owner/worker inventory differs")
                    }
                    val observedWorkersByLane =
                        task9ObservedOwnerSequences.entries.associate { (worker, owners) -> owners.toList() to worker }
                    if (
                        observedWorkersByLane.size == 5 &&
                            observedWorkersByLane.keys == task9OrderedLaneOwners.toSet()
                    ) {
                        val normalizedLanes =
                            task9OrderedLaneOwners.mapIndexed { index, owners ->
                                "W${index.inc()}=Gradle Test Executor ${observedWorkersByLane.getValue(owners)}"
                            }
                        logger.lifecycle("Task-9 ordered dispatch lanes: ${normalizedLanes.joinToString(", ")}")
                    } else {
                        logger.warn("Task-9 ordered dispatch observed lane sequence differs")
                    }
                }
            }

            override fun beforeTest(testDescriptor: TestDescriptor) {
                val owner = testDescriptor.className
                if (owner == null) {
                    logger.warn("Task-9 ordered dispatch class identity diagnostic unavailable")
                    return
                }
                if (owner !in task9OrderedTestOwners) {
                    logger.warn("Task-9 ordered dispatch encountered an unknown owner")
                    return
                }
                val worker = task9WorkerNumber(testDescriptor)
                if (worker == null) {
                    logger.warn("Task-9 ordered dispatch worker identity diagnostic unavailable")
                    return
                }
                synchronized(task9ObservationLock) {
                    val prior = task9ObservedWorkers.putIfAbsent(owner, worker)
                    if (prior != null && prior != worker) {
                        logger.warn("Task-9 ordered dispatch owner moved between workers")
                    }
                    val observedSequence = task9ObservedOwnerSequences.getOrPut(worker) { mutableListOf() }
                    if (owner !in observedSequence) observedSequence.add(owner)
                }
            }

            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) = Unit
        },
    )

    val workerTracePath = providers.environmentVariable("GASSTATION_TESTKIT_WORKER_TRACE").orNull
    val failureOutputPath = providers.environmentVariable("GASSTATION_TESTKIT_FAILURE_OUTPUT").orNull
    require((workerTracePath == null) == (failureOutputPath == null)) {
        "TestKit worker trace and failure output must be configured together"
    }
    if (workerTracePath != null) {
        check(failureOutputPath != null)
        val workerTrace = file(workerTracePath)
        val traceSession = workerTrace.parentFile
        val traceSessionId = traceSession.name.removePrefix("gasstation-metadata-capture-")
        require(
            workerTrace.name == "testkit-worker-events.tsv" &&
                traceSession.parentFile.path == "/tmp" &&
                traceSession.name.startsWith("gasstation-metadata-capture-") &&
                traceSessionId.isNotEmpty() &&
                traceSessionId.all { it.isLetterOrDigit() || it in "._-" },
        ) {
            "TestKit worker trace path is outside the sealed metadata session"
        }
        val failureOutput = file(failureOutputPath)
        require(
            failureOutput.parentFile.path == "/evidence-work/testkit-failures" &&
                failureOutput.name in setOf("metadata-capture-1", "metadata-capture-2"),
        ) {
            "TestKit failure output path is outside the sealed evidence volume"
        }
        val liveJunit = failureOutput
        val workerTraceLock = Any()
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val capturedOutput = mutableMapOf<Pair<String, String>, MutableMap<String, StringBuilder>>()
        fun encoded(value: String): String = encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
        fun boundedLive(value: String): String {
            val bytes = value.toByteArray(Charsets.UTF_8)
            val limit = 128 * 1024
            if (bytes.size <= limit) return value
            val prefix = "[truncated-live-prefix]\n"
            val prefixBytes = prefix.toByteArray(Charsets.UTF_8)
            return prefix + bytes.copyOfRange(bytes.size - (limit - prefixBytes.size), bytes.size).toString(Charsets.UTF_8)
        }
        fun xmlEscaped(value: String): String =
            buildString {
                value.forEach { character ->
                    when (character) {
                        '&' -> append("&amp;")
                        '<' -> append("&lt;")
                        '>' -> append("&gt;")
                        '"' -> append("&quot;")
                        '\'' -> append("&apos;")
                        '\t', '\n', '\r' -> append(character)
                        else -> if (character.code in 0x20..0xD7FF || character.code in 0xE000..0xFFFD) {
                            append(character)
                        } else {
                            append('\uFFFD')
                        }
                    }
                }
            }
        fun workerName(descriptor: TestDescriptor): String {
            var current = descriptor.parent
            while (current != null) {
                if (Regex("Gradle Test Executor [1-9][0-9]*").matches(current.name)) return current.name
                current = current.parent
            }
            throw GradleException("TestKit worker identity is unavailable")
        }
        fun appendTrace(line: String) {
            synchronized(workerTraceLock) {
                workerTrace.appendText(line + "\n", Charsets.UTF_8)
            }
        }

        doFirst {
            workerTrace.parentFile.mkdirs()
            check(workerTrace.createNewFile()) { "TestKit worker trace already exists" }
            val liveJunitParent = liveJunit.parentFile
            if (!liveJunitParent.exists()) {
                check(liveJunitParent.mkdir()) { "TestKit live JUnit parent could not be created" }
            }
            check(
                Files.isDirectory(liveJunitParent.toPath()) &&
                    !Files.isSymbolicLink(liveJunitParent.toPath()),
            ) {
                "TestKit live JUnit parent is missing or unsafe"
            }
            check(liveJunit.mkdir()) {
                "TestKit live JUnit stage is not fresh"
            }
        }
        finalizedBy(finalizeTask9TestKitFailureEvidence)
        addTestListener(
            object : TestListener {
                override fun beforeSuite(suite: TestDescriptor) = Unit

                override fun afterSuite(suite: TestDescriptor, result: TestResult) = Unit

                override fun beforeTest(testDescriptor: TestDescriptor) {
                    val className = testDescriptor.className ?: throw GradleException("TestKit class identity is unavailable")
                    appendTrace(
                        listOf(
                            "START",
                            encoded(workerName(testDescriptor)),
                            encoded(className),
                            encoded(testDescriptor.name),
                            System.currentTimeMillis().toString(),
                        ).joinToString("\t"),
                    )
                }

                override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
                    val className = testDescriptor.className ?: throw GradleException("TestKit class identity is unavailable")
                    val name = testDescriptor.name
                    val durationMillis = result.endTime - result.startTime
                    val durationSeconds =
                        "${durationMillis / 1000}.${(durationMillis % 1000).toString().padStart(3, '0')}"
                    synchronized(workerTraceLock) {
                        workerTrace.appendText(
                            listOf(
                                "END",
                                encoded(workerName(testDescriptor)),
                                encoded(className),
                                encoded(name),
                                result.resultType.name,
                                durationMillis.toString(),
                            ).joinToString("\t") + "\n",
                            Charsets.UTF_8,
                        )
                        val output = capturedOutput.remove(className to name).orEmpty()
                        val failure = result.resultType == TestResult.ResultType.FAILURE
                        val skipped = result.resultType == TestResult.ResultType.SKIPPED
                        val exceptions = result.exceptions
                        val exception = exceptions.firstOrNull()
                        val exceptionMessage =
                            boundedLive(exceptions.joinToString("\n") { throwable -> throwable.message.orEmpty() })
                        val exceptionBody =
                            boundedLive(
                                exceptions.joinToString("\nCaused by recorded exception:\n") { throwable ->
                                    throwable.stackTraceToString()
                                },
                            )
                        val suiteName = "$className#$name"
                        val xml =
                            buildString {
                                append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                                append(
                                    "<testsuite name=\"${xmlEscaped(suiteName)}\" tests=\"1\" " +
                                        "failures=\"${if (failure) 1 else 0}\" errors=\"0\" " +
                                        "skipped=\"${if (skipped) 1 else 0}\" time=\"$durationSeconds\">\n",
                                )
                                append(
                                    "  <testcase classname=\"${xmlEscaped(className)}\" " +
                                        "name=\"${xmlEscaped(name)}\" time=\"$durationSeconds\">",
                                )
                                when {
                                    failure -> {
                                        val type = exception?.javaClass?.name ?: "org.gradle.api.tasks.testing.TestFailure"
                                        val message = exceptionMessage.ifEmpty { "Test failed without a surfaced exception" }
                                        append(
                                            "<failure type=\"${xmlEscaped(type)}\" message=\"${xmlEscaped(message)}\">" +
                                                xmlEscaped(exceptionBody) + "</failure>",
                                        )
                                    }
                                    skipped -> append("<skipped/>")
                                }
                                append("</testcase>\n")
                                output["StdOut"]?.let { append("  <system-out>${xmlEscaped(it.toString())}</system-out>\n") }
                                output["StdErr"]?.let { append("  <system-err>${xmlEscaped(it.toString())}</system-err>\n") }
                                append("</testsuite>\n")
                            }
                        val identity = "$className\u0000$name"
                        val digest =
                            MessageDigest
                                .getInstance("SHA-256")
                                .digest(identity.toByteArray(Charsets.UTF_8))
                                .joinToString("") { byte -> "%02x".format(byte) }
                        val destination = liveJunit.toPath().resolve("TEST-$digest.xml")
                        val temporary = liveJunit.toPath().resolve(".TEST-$digest.xml.tmp")
                        Files.writeString(temporary, xml, Charsets.UTF_8)
                        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE)
                    }
                }
            },
        )
        addTestOutputListener(
            object : TestOutputListener {
                override fun onOutput(testDescriptor: TestDescriptor, outputEvent: TestOutputEvent) {
                    val className = testDescriptor.className ?: throw GradleException("TestKit output class identity is unavailable")
                    synchronized(workerTraceLock) {
                        val name = testDescriptor.name
                        workerTrace.appendText(
                            listOf(
                                "OUTPUT",
                                encoded(workerName(testDescriptor)),
                                encoded(className),
                                encoded(name),
                                outputEvent.destination.name,
                                encoded(outputEvent.message),
                            ).joinToString("\t") + "\n",
                            Charsets.UTF_8,
                        )
                        val destinationOutput =
                            capturedOutput
                            .getOrPut(className to name) { mutableMapOf() }
                            .getOrPut(outputEvent.destination.name) { StringBuilder() }
                        check(
                            (destinationOutput.toString() + outputEvent.message).toByteArray(Charsets.UTF_8).size <= 64 * 1024,
                        ) {
                            "TestKit owned output exceeds the live evidence limit"
                        }
                        destinationOutput.append(outputEvent.message)
                    }
                }
            },
        )
    }
}

gradlePlugin {
    plugins {
        register("gasStationAndroidApplicationCompose") {
            id = "gasstation.android.application.compose"
            implementationClass = "GasStationAndroidApplicationComposeConventionPlugin"
        }
        register("gasStationAndroidLibrary") {
            id = "gasstation.android.library"
            implementationClass = "GasStationAndroidLibraryConventionPlugin"
        }
        register("gasStationAndroidLibraryCompose") {
            id = "gasstation.android.library.compose"
            implementationClass = "GasStationAndroidLibraryComposeConventionPlugin"
        }
        register("gasStationJvmLibrary") {
            id = "gasstation.jvm.library"
            implementationClass = "GasStationJvmLibraryConventionPlugin"
        }
        register("gasStationAndroidHilt") {
            id = "gasstation.android.hilt"
            implementationClass = "GasStationAndroidHiltConventionPlugin"
        }
        register("gasStationAndroidRoom") {
            id = "gasstation.android.room"
            implementationClass = "GasStationAndroidRoomConventionPlugin"
        }
        register("androidSpotless") {
            id = "gasstation.spotless"
            implementationClass = "GasStationSpotlessConventionPlugin"
        }
        register("roborazzi") {
            id = "gasstation.roborazzi"
            implementationClass = "GasStationRoborazziConventionPlugin"
        }
        register("gasStationRootQuality") {
            id = "gasstation.root.quality"
            implementationClass =
                "com.gasstation.buildlogic.quality.GasStationRootQualityConventionPlugin"
        }
        register("gasStationJvmMutation") {
            id = "gasstation.jvm.mutation"
            implementationClass =
                "com.gasstation.buildlogic.quality.mutation.GasStationJvmMutationConventionPlugin"
        }
    }
}
