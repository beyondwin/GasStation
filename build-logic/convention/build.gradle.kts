import java.nio.file.Files
import java.nio.file.Path as NioPath
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
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
// fixture version while preparing the read-only dependency cache.
val testKitDependencySeeds =
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

val testKitReadOnlyDependencyCache =
    layout.buildDirectory.dir("testkit-read-only-dependency-cache")

val prepareTestKitReadOnlyDependencyCache =
    tasks.register("prepareTestKitReadOnlyDependencyCache") {
        group = "verification"
        description = "Copies the exact AAPT2 cache into a TestKit-safe shared read-only dependency seed."
        notCompatibleWithConfigurationCache("TestKit read-only dependency-cache preparation is a fresh seed")
        outputs.dir(testKitReadOnlyDependencyCache)
        outputs.upToDateWhen { false }
        doLast {
            testKitDependencySeeds.forEach { configuration -> configuration.files.forEach(File::getName) }
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
    timeout.set(Duration.ofMinutes(15))
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
