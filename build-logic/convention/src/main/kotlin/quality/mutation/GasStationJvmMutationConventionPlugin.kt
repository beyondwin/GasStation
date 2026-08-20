package com.gasstation.buildlogic.quality.mutation

import info.solidsoft.gradle.pitest.GasStationVerifiedPitestTask
import info.solidsoft.gradle.pitest.PitestPluginExtension
import info.solidsoft.gradle.pitest.PitestTask
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.JavaPluginExtension as JavaExtension
import org.gradle.api.provider.HasConfigurableValue
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import java.math.BigDecimal
import java.io.File
import java.nio.charset.StandardCharsets

private data class MutationModule(
    val projectPath: String,
    val packageRoot: String,
    val blockingThreshold: Int?,
)

private val mutationModules =
    listOf(
        MutationModule(":domain:station", "com.gasstation.domain.station", 45),
        MutationModule(":domain:location", "com.gasstation.domain.location", 75),
        MutationModule(":domain:settings", "com.gasstation.domain.settings", null),
    )

class GasStationJvmMutationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        requireSupportedMutationProject(target.path)
        val module = mutationModules.single { it.projectPath == target.path }
        target.pluginManager.apply("gasstation.jvm.library")
        target.pluginManager.apply("info.solidsoft.pitest")

        val sourceSets = target.extensions.getByType<SourceSetContainer>()
        val main = sourceSets.named("main")
        val test = sourceSets.named("test")
        val packageGlob = "${module.packageRoot}.*"
        val extension = target.extensions.getByType<PitestPluginExtension>()
        extension.apply {
            pitestVersion.set("1.25.7")
            targetClasses.set(setOf(packageGlob))
            targetTests.set(setOf(packageGlob))
            mainSourceSets.set(main.map { setOf(it) })
            testSourceSets.set(test.map { setOf(it) })
            additionalMutableCodePaths.set(emptySet())
            fileExtensionsToFilter.set(listOf("pom", "so", "dll", "dylib"))
            addJUnitPlatformLauncher.set(false)
            threads.set(2)
            mutators.set(setOf("DEFAULTS"))
            outputFormats.set(setOf("HTML", "XML"))
            timestampedReports.set(false)
            failWhenNoMutations.set(true)
            skipFailingTests.set(false)
            fullMutationMatrix.set(false)
            detectInlinedCode.set(true)
            enableDefaultIncrementalAnalysis.set(false)
            timeoutFactor.set(BigDecimal("1.25"))
            timeoutConstInMillis.set(4000)
            inputCharset.set(StandardCharsets.UTF_8)
            outputCharset.set(StandardCharsets.UTF_8)
            mutationEngine.set("gregor")
            useClasspathFile.set(true)
            useClasspathJar.set(false)
            verbose.set(false)
            verbosity.set("NO_SPINNER")
            excludedClasses.set(emptySet())
            excludedMethods.set(emptySet())
            excludedTestClasses.set(emptySet())
            avoidCallsTo.set(emptySet())
            includedGroups.set(emptySet())
            excludedGroups.set(emptySet())
            includedTestMethods.set(emptySet())
            features.set(emptyList())
            pluginConfiguration.set(emptyMap())
            jvmArgs.set(emptyList())
            mainProcessJvmArgs.set(emptyList())
            exportLineCoverage.set(false)
            reportDir.set(target.layout.buildDirectory.dir("reports/pitest"))
            module.blockingThreshold?.let(mutationThreshold::set)
        }
        lockExtension(extension)

        val original = target.tasks.named<PitestTask>("pitest").get()
        original.apply {
            actions.add(0, RejectDirectPitestAction("${target.path}:pitestVerified"))
        }

        val childHome = target.rootProject.layout.buildDirectory.dir("quality/pitest-runtime/pit-child-home")
        val childTmp = target.rootProject.layout.buildDirectory.dir("quality/pitest-runtime/pit-child-tmp")
        val childEnvironment = target.providers.provider {
            sortedMapOf(
                "CI" to "true",
                "HOME" to childHome.get().asFile.absolutePath,
                "LANG" to "C",
                "LC_ALL" to "C",
                "TERM" to "dumb",
                "TMPDIR" to childTmp.get().asFile.absolutePath,
                "TZ" to "UTC",
            )
        }
        val toolchains = target.extensions.getByType<JavaToolchainService>()
        val java21 = toolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.ADOPTIUM)
        }
        val verified = target.tasks.register<GasStationVerifiedPitestTask>("pitestVerified") {
            group = "verification"
            description = "Runs PIT through the sealed GasStation execution surface."
            copyPitestPropertiesFrom(original)
            expectedChildEnvironment.set(childEnvironment)
            expectedRepositoryRoot.set(target.rootProject.layout.projectDirectory)
            expectedSourceDirs.from(original.sourceDirs)
            expectedAdditionalClasspath.from(original.additionalClasspath)
            expectedMutableCodePaths.from(original.mutableCodePaths)
            expectedLaunchClasspath.from(original.launchClasspath)
            module.blockingThreshold?.let(expectedMutationThreshold::set)
            javaLauncher.set(java21)
            executable(java21.get().executablePath.asFile.absolutePath)
            workingDir(target.rootProject.layout.projectDirectory)
            environment.clear()
            environment(childEnvironment.get())
            configureSealedInheritedJavaExecDefaults(this)
            classpath(original.launchClasspath)
            dependsOn("verifyPitestConfiguration")
        }
        target.afterEvaluate {
            val task = verified.get()
            val snapshot = task.effectivePitestSurface(target.rootProject.projectDir, resolveFiles = false).toMutableMap()
            appendExtensionSurface(snapshot, extension)
            validateCanonicalSurface(snapshot, module)
            task.expectedEffectiveSurface.set(snapshot.toSortedMap())
            task.expectedEffectiveSurface.finalizeValue()
            task.expectedEffectiveSurface.disallowChanges()
        }

        target.tasks.register<VerifyPitestConfigurationTask>("verifyPitestConfiguration") {
            group = "verification"
            description = "Validates and records the effective sealed PIT configuration."
            projectPathInput.set(target.path)
            targetGlob.set(verified.flatMap { it.targetClasses }.map { it.single() })
            pitestVersion.set(extension.pitestVersion)
            threads.set(verified.flatMap { it.threads })
            enforcementPhase.set("blocking")
            module.blockingThreshold?.let(mutationThreshold::set)
            effectiveValues.set(target.providers.provider {
                verified.get().effectivePitestSurface(target.rootProject.projectDir, resolveFiles = false).toMutableMap().also {
                    appendExtensionSurface(it, extension)
                }.toSortedMap()
            })
            expectedEffectiveValues.set(verified.flatMap { it.expectedEffectiveSurface })
            repositoryRoot.set(target.rootProject.layout.projectDirectory)
            actualSourceDirs.from(verified.get().sourceDirs)
            expectedSourceDirs.from(verified.get().expectedSourceDirs)
            actualAdditionalClasspath.from(verified.get().additionalClasspath)
            expectedAdditionalClasspath.from(verified.get().expectedAdditionalClasspath)
            actualMutableCodePaths.from(verified.get().mutableCodePaths)
            expectedMutableCodePaths.from(verified.get().expectedMutableCodePaths)
            actualLaunchClasspath.from(verified.get().launchClasspath)
            expectedLaunchClasspath.from(verified.get().expectedLaunchClasspath)
            directPitestGuardMarker.set("RejectDirectPitestAction:first")
            directPitestGuardMarker.disallowChanges()
            policyFile.set(target.rootProject.layout.projectDirectory.file("config/quality/mutation-policy.json"))
            routeFile.set(target.rootProject.layout.projectDirectory.file("build/reports/pitest/route.json"))
            routeReceiptFile.set(target.rootProject.layout.projectDirectory.file("build/reports/pitest/route-receipt.json"))
            outputFile.set(target.layout.buildDirectory.file("reports/quality/pitest-configuration.json"))
            outputFile.disallowChanges()
        }
    }
}

private fun appendExtensionSurface(
    surface: MutableMap<String, String>,
    extension: PitestPluginExtension,
) {
    fun values(raw: Iterable<*>?): String = raw?.map { item ->
        when (item) {
            is org.gradle.api.tasks.SourceSet -> item.name
            is File -> item.name
            else -> item.toString()
        }
    }?.sorted()?.joinToString("\u001f") ?: "<null>"
    fun value(raw: Any?): String = raw?.toString() ?: "<null>"
    surface.putAll(
        mapOf(
            "extension.pitestVersion" to value(extension.pitestVersion.orNull),
            "extension.testPlugin" to value(extension.testPlugin.orNull),
            "extension.junit5PluginVersion" to value(extension.junit5PluginVersion.orNull),
            "extension.mainSourceSets" to values(extension.mainSourceSets.orNull),
            "extension.testSourceSets" to values(extension.testSourceSets.orNull),
            "extension.additionalMutableCodePaths" to values(extension.additionalMutableCodePaths.orNull),
            "extension.fileExtensionsToFilter" to values(extension.fileExtensionsToFilter.orNull),
            "extension.addJUnitPlatformLauncher" to value(extension.addJUnitPlatformLauncher.orNull),
        ),
    )
}

private fun validateCanonicalSurface(surface: Map<String, String>, module: MutationModule) {
    val expected = mapOf(
        "extension.pitestVersion" to "1.25.7",
        "extension.testPlugin" to "<null>",
        "extension.junit5PluginVersion" to "<null>",
        "extension.mainSourceSets" to "main",
        "extension.testSourceSets" to "test",
        "extension.additionalMutableCodePaths" to "",
        "extension.fileExtensionsToFilter" to "dll\u001fdylib\u001fpom\u001fso",
        "extension.addJUnitPlatformLauncher" to "false",
        "pit.testPlugin" to "<null>",
        "pit.targetClasses" to "${module.packageRoot}.*",
        "pit.targetTests" to "${module.packageRoot}.*",
        "pit.threads" to "2",
        "pit.mutators" to "DEFAULTS",
        "pit.excludedMethods" to "",
        "pit.excludedClasses" to "",
        "pit.excludedTestClasses" to "",
        "pit.avoidCallsTo" to "",
        "pit.verbose" to "false",
        "pit.verbosity" to "NO_SPINNER",
        "pit.timeoutFactor" to "1.25",
        "pit.timeoutConstInMillis" to "4000",
        "pit.childProcessJvmArgs" to "",
        "pit.outputFormats" to "HTML\u001fXML",
        "pit.failWhenNoMutations" to "true",
        "pit.skipFailingTests" to "false",
        "pit.includedGroups" to "",
        "pit.excludedGroups" to "",
        "pit.fullMutationMatrix" to "false",
        "pit.includedTestMethods" to "",
        "pit.detectInlinedCode" to "true",
        "pit.timestampedReports" to "false",
        "pit.useClasspathFile" to "true",
        "pit.historyInputLocation" to "<null>",
        "pit.historyOutputLocation" to "<null>",
        "pit.enableDefaultIncrementalAnalysis" to "false",
        "pit.mutationThreshold" to (module.blockingThreshold?.toString() ?: "<null>"),
        "pit.coverageThreshold" to "<null>",
        "pit.testStrengthThreshold" to "<null>",
        "pit.mutationEngine" to "gregor",
        "pit.exportLineCoverage" to "false",
        "pit.jvmPath" to "<null>",
        "pit.mainProcessJvmArgs" to "",
        "pit.pluginConfiguration" to "",
        "pit.maxSurviving" to "<null>",
        "pit.useClasspathJar" to "false",
        "pit.inputEncoding" to "UTF-8",
        "pit.outputEncoding" to "UTF-8",
        "pit.features" to "",
        "cli.overriddenTargetTests" to "<null>",
        "cli.additionalFeatures" to "<null>",
        "cli.overriddenVerbose" to "<null>",
        "java.args" to "",
        "java.argumentProviderCount" to "0",
        "java.jvmArgs" to "",
        "java.jvmArguments" to "",
        "java.jvmArgumentProviderCount" to "0",
        "java.systemProperties" to "",
        "java.minHeapSize" to "<null>",
        "java.maxHeapSize" to "<null>",
        "java.enableAssertions" to "false",
        "java.debug" to "false",
        "java.debug.enabled" to "false",
        "java.debug.host" to "localhost",
        "java.debug.port" to "5005",
        "java.debug.server" to "true",
        "java.debug.suspend" to "true",
        "java.defaultCharacterEncoding" to "UTF-8",
        "java.mainClass" to "org.pitest.mutationtest.commandline.MutationCoverageReport",
        "java.mainModule" to "<null>",
        "java.inferModulePath" to "false",
        "java.ignoreExitValue" to "false",
        "command.managedEncodingArguments" to "-Dfile.encoding=UTF-8",
    )
    val changed = expected.filter { (name, value) -> surface[name] != value }
    if (changed.isNotEmpty()) {
        throw GradleException("PIT canonical effective surface differs: ${changed.keys.sorted().joinToString(",")}")
    }
}

internal fun configureSealedInheritedJavaExecDefaults(task: JavaExec) {
    task.defaultCharacterEncoding = "UTF-8"
    task.modularity.inferModulePath.set(false)
    task.debugOptions.enabled.set(false)
    task.debugOptions.host.set("localhost")
    task.debugOptions.port.set(5005)
    task.debugOptions.server.set(true)
    task.debugOptions.suspend.set(true)
}

internal fun requireSupportedMutationProject(projectPath: String) {
    if (mutationModules.none { it.projectPath == projectPath }) {
        throw GradleException(
            "gasstation.jvm.mutation supports exactly " +
                mutationModules.joinToString(",") { it.projectPath },
        )
    }
}

internal fun blockingMutationThreshold(projectPath: String): Int? {
    val module = mutationModules.singleOrNull { it.projectPath == projectPath }
        ?: throw GradleException("Unsupported mutation project: $projectPath")
    return module.blockingThreshold
}

private fun GasStationVerifiedPitestTask.copyPitestPropertiesFrom(original: PitestTask) {
    testPlugin.set(original.testPlugin)
    reportDir.set(original.reportDir)
    targetClasses.set(original.targetClasses)
    targetTests.set(original.targetTests)
    threads.set(original.threads)
    mutators.set(original.mutators)
    excludedMethods.set(original.excludedMethods)
    excludedClasses.set(original.excludedClasses)
    excludedTestClasses.set(original.excludedTestClasses)
    avoidCallsTo.set(original.avoidCallsTo)
    verbose.set(original.verbose)
    verbosity.set(original.verbosity)
    timeoutFactor.set(original.timeoutFactor)
    timeoutConstInMillis.set(original.timeoutConstInMillis)
    childProcessJvmArgs.set(original.childProcessJvmArgs)
    outputFormats.set(original.outputFormats)
    failWhenNoMutations.set(original.failWhenNoMutations)
    skipFailingTests.set(original.skipFailingTests)
    includedGroups.set(original.includedGroups)
    excludedGroups.set(original.excludedGroups)
    fullMutationMatrix.set(original.fullMutationMatrix)
    includedTestMethods.set(original.includedTestMethods)
    sourceDirs.from(original.sourceDirs)
    detectInlinedCode.set(original.detectInlinedCode)
    timestampedReports.set(original.timestampedReports)
    additionalClasspath.from(original.additionalClasspath)
    useAdditionalClasspathFile.set(original.useAdditionalClasspathFile)
    additionalClasspathFile.set(original.additionalClasspathFile)
    mutableCodePaths.from(original.mutableCodePaths)
    historyInputLocation.set(original.historyInputLocation)
    historyOutputLocation.set(original.historyOutputLocation)
    enableDefaultIncrementalAnalysis.set(original.enableDefaultIncrementalAnalysis)
    defaultFileForHistoryData.set(original.defaultFileForHistoryData)
    mutationThreshold.set(original.mutationThreshold)
    coverageThreshold.set(original.coverageThreshold)
    testStrengthThreshold.set(original.testStrengthThreshold)
    mutationEngine.set(original.mutationEngine)
    exportLineCoverage.set(original.exportLineCoverage)
    jvmPath.set(original.jvmPath)
    mainProcessJvmArgs.set(original.mainProcessJvmArgs)
    launchClasspath.from(original.launchClasspath)
    pluginConfiguration.set(original.pluginConfiguration)
    maxSurviving.set(original.maxSurviving)
    useClasspathJar.set(original.useClasspathJar)
    inputEncoding.set(original.inputEncoding)
    outputEncoding.set(original.outputEncoding)
    features.set(original.features)
}

private fun lockExtension(extension: PitestPluginExtension) {
    listOf<HasConfigurableValue>(
        extension.pitestVersion, extension.testPlugin, extension.junit5PluginVersion,
        extension.reportDir, extension.targetClasses, extension.targetTests, extension.threads,
        extension.mutators, extension.excludedMethods, extension.excludedClasses,
        extension.excludedTestClasses, extension.avoidCallsTo, extension.verbose,
        extension.verbosity, extension.timeoutFactor, extension.timeoutConstInMillis,
        extension.jvmArgs, extension.outputFormats, extension.failWhenNoMutations,
        extension.skipFailingTests, extension.includedGroups, extension.excludedGroups,
        extension.fullMutationMatrix, extension.includedTestMethods, extension.testSourceSets,
        extension.mainSourceSets, extension.detectInlinedCode, extension.timestampedReports,
        extension.useClasspathFile, extension.additionalMutableCodePaths,
        extension.historyInputLocation, extension.historyOutputLocation,
        extension.enableDefaultIncrementalAnalysis, extension.mutationThreshold,
        extension.coverageThreshold, extension.testStrengthThreshold, extension.mutationEngine,
        extension.exportLineCoverage, extension.jvmPath, extension.mainProcessJvmArgs,
        extension.pluginConfiguration, extension.maxSurviving, extension.useClasspathJar,
        extension.inputCharset, extension.outputCharset, extension.features,
        extension.fileExtensionsToFilter, extension.addJUnitPlatformLauncher,
    ).forEach {
        it.finalizeValue()
        it.disallowChanges()
    }
}
