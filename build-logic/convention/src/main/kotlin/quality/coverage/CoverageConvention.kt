package com.gasstation.buildlogic.quality.coverage

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HasUnitTest
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

internal fun configureCoverage(root: Project) {
    val rawModules =
        root.gradle.extensions.extraProperties.properties["gasstation.activeModulePaths"]
            ?: throw GradleException("settings.gradle.kts must publish gasstation.activeModulePaths")
    if (rawModules !is List<*> || rawModules.any { it !is String }) {
        throw GradleException("gasstation.activeModulePaths must be an immutable List<String>")
    }
    val buildModules = rawModules.filterIsInstance<String>().also { modules ->
        if (modules.size != modules.toSet().size || modules.any { !it.matches(Regex(":(?:[A-Za-z0-9_-]+:?)+")) }) {
            throw GradleException("gasstation.activeModulePaths contains duplicate or non-canonical paths")
        }
    }.sorted()
    val gradleProjects = root.subprojects.map(Project::getPath).sorted()
    val missingProjects = buildModules - gradleProjects.toSet()
    if (missingProjects.isNotEmpty()) throw GradleException("Explicit build modules are absent from Gradle: $missingProjects")

    val sourceCommit = root.providers.gradleProperty("gasstation.coverageSourceCommit")
    val manifest = root.layout.buildDirectory.file("reports/coverage/report-manifest.json")
    val index = root.tasks.register<WriteCoverageManifestIndexTask>("writeCoverageManifestIndex") {
        this.sourceCommit.set(sourceCommit)
        this.gradleProjects.set(gradleProjects)
        this.buildModules.set(buildModules)
        reviewedExcludedModules.set(listOf(":benchmark"))
        repositoryRoot.set(root.layout.projectDirectory)
        outputFile.set(manifest)
    }
    root.tasks.register("coverageXmlReport") {
        group = "verification"
        description = "Runs provider-owned unit coverage reports and writes their deterministic manifest."
        dependsOn(index)
    }
    root.tasks.register<VerifyCoverageReportTask>("verifyCoverageReport") {
        group = "verification"
        description = "Verifies authored-source coverage provenance, baselines, and changed-code ratchets."
        dependsOn("coverageXmlReport")
        this.manifest.set(manifest)
        policy.set(root.layout.projectDirectory.file("config/quality/coverage-policy.json"))
        baseline.set(root.layout.projectDirectory.file("config/quality/coverage-baseline.json"))
        verifier.set(root.layout.projectDirectory.file("scripts/quality/verify_coverage.py"))
        this.sourceCommit.set(sourceCommit)
        event.set(root.providers.gradleProperty("gasstation.coverageEvent").orElse("local"))
        baseRef.set(root.providers.gradleProperty("gasstation.coverageBaseRef"))
        summary.set(root.layout.buildDirectory.file("reports/coverage/verification-summary.json"))
    }

    root.subprojects.forEach { module ->
        if (module.path !in buildModules || module.path == ":benchmark") return@forEach
        module.pluginManager.withPlugin("gasstation.jvm.library") {
            registerJvmCoverage(module, root, sourceCommit, index)
        }
        module.pluginManager.withPlugin("com.android.library") {
            val components = module.extensions.getByType<LibraryAndroidComponentsExtension>()
            registerAndroidCoverage(components, module, root, sourceCommit, index)
        }
        module.pluginManager.withPlugin("com.android.application") {
            val components = module.extensions.getByType<ApplicationAndroidComponentsExtension>()
            registerAndroidCoverage(components, module, root, sourceCommit, index)
        }
    }
}

private fun registerJvmCoverage(
    module: Project,
    root: Project,
    sourceCommit: org.gradle.api.provider.Provider<String>,
    index: TaskProvider<WriteCoverageManifestIndexTask>,
) {
    applyPinnedJacoco(module)
    val java = module.extensions.getByType<JavaPluginExtension>()
    val main = java.sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME)
    val test = java.sourceSets.named(SourceSet.TEST_SOURCE_SET_NAME)
    val testTask = module.tasks.named<Test>(test.get().getTaskName(null, null))
    val sources = module.objects.fileCollection().from(main.map { it.allSource.srcDirs })
    val testSources = module.objects.fileCollection().from(test.map { it.allSource.srcDirs })
    val roots = main.map { sourceSet -> sourceRoots(root.rootDir, sourceSet.allSource.srcDirs) }
    val testRoots = test.map { sourceSet -> sourceRoots(root.rootDir, sourceSet.allSource.srcDirs) }
    val prepare = module.tasks.register<PrepareCoverageClassesTask>("prepareCoverageMainClasses") {
        dependsOn(main.map { it.output })
        inputJars.set(emptyList())
        inputDirectories.set(main.map { sourceSet ->
            sourceSet.output.classesDirs.elements.get().map { location ->
                module.layout.projectDirectory.dir(
                    location.asFile.relativeTo(module.projectDir).invariantSeparatorsPath,
                )
            }
        })
        excludedClassPatterns.set(REVIEWED_CLASS_EXCLUDES)
        outputDirectory.set(module.layout.buildDirectory.dir("reports/coverage/main/prepared-classes"))
    }
    registerReport(
        module = module,
        root = root,
        sourceCommit = sourceCommit,
        index = index,
        reportName = "main",
        platform = "jvm",
        testTask = testTask,
        prepare = prepare,
        sourceDirectories = sources,
        testSourceDirectories = testSources,
        sourceRoots = roots,
        testSourceRoots = testRoots,
    )
}

private fun <VariantT : Variant> registerAndroidCoverage(
    components: AndroidComponentsExtension<*, *, VariantT>,
    module: Project,
    root: Project,
    sourceCommit: org.gradle.api.provider.Provider<String>,
    index: TaskProvider<WriteCoverageManifestIndexTask>,
) {
    applyPinnedJacoco(module)
    components.onVariants(components.selector().withBuildType("debug")) { variant ->
        val unitTest = (variant as? HasUnitTest)?.unitTest ?: return@onVariants
        val reportName = variant.name
        val suffix = reportName.replaceFirstChar(Char::uppercase)
        val expectedTaskName = variant.computeTaskName("test", "UnitTest")
        val exactTests = module.tasks.withType<Test>().matching { it.name == expectedTaskName }
        val variantJava = requireNotNull(variant.sources.java) { "AGP variant Java source provider is required" }
        val variantKotlin = requireNotNull(variant.sources.kotlin) { "AGP variant Kotlin source provider is required" }
        val testJava = requireNotNull(unitTest.sources.java) { "AGP unit-test Java source provider is required" }
        val testKotlin = requireNotNull(unitTest.sources.kotlin) { "AGP unit-test Kotlin source provider is required" }
        val sources = module.objects.fileCollection().from(variantJava.static, variantKotlin.static)
        val testSources = module.objects.fileCollection().from(testJava.static, testKotlin.static)
        val roots = variantJava.static.zip(variantKotlin.static) { java, kotlin ->
            sourceRoots(root.rootDir, java.map { it.asFile }.toSet() + kotlin.map { it.asFile })
        }
        val testRoots = testJava.static.zip(testKotlin.static) { java, kotlin ->
            sourceRoots(root.rootDir, java.map { it.asFile }.toSet() + kotlin.map { it.asFile })
        }
        val prepare = module.tasks.register<PrepareCoverageClassesTask>("prepareCoverage${suffix}Classes") {
            excludedClassPatterns.set(REVIEWED_CLASS_EXCLUDES)
            outputDirectory.set(module.layout.buildDirectory.dir("reports/coverage/$reportName/prepared-classes"))
        }
        variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).use(prepare).toGet(
            ScopedArtifact.CLASSES,
            PrepareCoverageClassesTask::inputJars,
            PrepareCoverageClassesTask::inputDirectories,
        )
        val registered = registerReport(
            module = module,
            root = root,
            sourceCommit = sourceCommit,
            index = index,
            reportName = reportName,
            platform = "android",
            testTask = null,
            liveTestTasks = exactTests,
            expectedTestTaskName = expectedTaskName,
            prepare = prepare,
            sourceDirectories = sources,
            testSourceDirectories = testSources,
            sourceRoots = roots,
            testSourceRoots = testRoots,
        )
        unitTest.configureTestTask { concrete ->
            if (concrete.name != expectedTaskName || concrete.path != "${module.path}:$expectedTaskName") {
                throw GradleException("AGP unit-test task identity mismatch for ${module.path}|$reportName: ${concrete.path}")
            }
            concrete.extensions.getByType<JacocoTaskExtension>().apply {
                // Robolectric executes transformed Android classes without source locations. Keep
                // those probes, otherwise valid Android unit coverage is silently reported as zero.
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
            registered.executionData.from(concrete.extensions.getByType<JacocoTaskExtension>().destinationFile)
        }
    }
}

private fun applyPinnedJacoco(module: Project) {
    module.pluginManager.apply("jacoco")
    module.extensions.getByType<JacocoPluginExtension>().toolVersion = "0.8.15"
}

private data class RegisteredCoverage(
    val report: TaskProvider<JacocoReport>,
    val entry: TaskProvider<WriteCoverageManifestEntryTask>,
    val executionData: org.gradle.api.file.ConfigurableFileCollection,
)

private fun registerReport(
    module: Project,
    root: Project,
    sourceCommit: org.gradle.api.provider.Provider<String>,
    index: TaskProvider<WriteCoverageManifestIndexTask>,
    reportName: String,
    platform: String,
    testTask: TaskProvider<Test>?,
    liveTestTasks: org.gradle.api.tasks.TaskCollection<Test>? = null,
    expectedTestTaskName: String = testTask?.name.orEmpty(),
    prepare: TaskProvider<PrepareCoverageClassesTask>,
    sourceDirectories: org.gradle.api.file.FileCollection,
    testSourceDirectories: org.gradle.api.file.FileCollection,
    sourceRoots: org.gradle.api.provider.Provider<List<String>>,
    testSourceRoots: org.gradle.api.provider.Provider<List<String>>,
): RegisteredCoverage {
    val suffix = reportName.replaceFirstChar(Char::uppercase)
    val xmlOutput = module.layout.buildDirectory.file("reports/coverage/$reportName/report.xml")
    val executionFiles = module.objects.fileCollection()
    if (testTask != null) {
        executionFiles.from(testTask.map { it.extensions.getByType<JacocoTaskExtension>().destinationFile })
    }
    val report = module.tasks.register<JacocoReport>("coverage${suffix}XmlReport") {
        dependsOn(prepare)
        if (testTask != null) dependsOn(testTask) else liveTestTasks?.let { dependsOn(it) }
        classDirectories.from(prepare.flatMap { it.outputDirectory })
        this.sourceDirectories.from(sourceDirectories)
        this.additionalSourceDirs.from(sourceDirectories)
        executionData(executionFiles)
        reports {
            xml.required.set(true)
            xml.outputLocation.set(xmlOutput)
            html.required.set(false)
            csv.required.set(false)
        }
    }
    val entry = module.tasks.register<WriteCoverageManifestEntryTask>("writeCoverage${suffix}ManifestEntry") {
        dependsOn(report)
        this.sourceCommit.set(sourceCommit)
        reportId.set("${module.path}|$reportName")
        modulePath.set(module.path)
        this.platform.set(platform)
        variant.set(reportName)
        testTaskPath.set("${module.path}:$expectedTestTaskName")
        repositoryRoot.set(root.layout.projectDirectory)
        this.sourceRoots.set(sourceRoots)
        this.testSourceRoots.set(testSourceRoots)
        sourceFiles.from(sourceDirectories.asFileTree.matching { include("**/*.kt", "**/*.java") })
        testSourceFiles.from(testSourceDirectories.asFileTree.matching { include("**/*.kt", "**/*.java") })
        preparedClassDirectory.set(prepare.flatMap { it.outputDirectory })
        executionData.from(executionFiles)
        xmlReport.set(xmlOutput)
        outputFile.set(module.layout.buildDirectory.file("reports/coverage/$reportName/manifest-entry.json"))
    }
    index.configure {
        dependsOn(entry)
        entryFiles.from(entry.flatMap { it.outputFile })
    }
    return RegisteredCoverage(report, entry, executionFiles)
}

private fun sourceRoots(repositoryRoot: File, directories: Set<File>): List<String> =
    directories.filter { directory ->
        directory.name in setOf("java", "kotlin") && directory.canonicalFile.toPath().startsWith(repositoryRoot.canonicalFile.toPath())
    }.map { it.canonicalFile.relativeTo(repositoryRoot.canonicalFile).invariantSeparatorsPath }.sorted()

private val REVIEWED_CLASS_EXCLUDES = listOf(
    "**/*Hilt_*.class",
    "**/*_HiltModules*.class",
    "**/*_Factory*.class",
    "**/*_Provide*.class",
    "**/*ComposableSingletons*.class",
)
