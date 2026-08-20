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
import java.nio.charset.StandardCharsets

private data class MutationModule(
    val projectPath: String,
    val packageRoot: String,
    val observationThreshold: Int?,
)

private val mutationModules =
    listOf(
        MutationModule(":domain:station", "com.gasstation.domain.station", 40),
        MutationModule(":domain:location", "com.gasstation.domain.location", null),
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
            module.observationThreshold?.let(mutationThreshold::set)
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
            module.observationThreshold?.let(expectedMutationThreshold::set)
            javaLauncher.set(java21)
            workingDir(target.rootProject.layout.projectDirectory)
            environment.clear()
            environment(childEnvironment.get())
            configureSealedInheritedJavaExecDefaults(this)
            classpath(original.launchClasspath)
            dependsOn("verifyPitestConfiguration")
        }

        target.tasks.register<VerifyPitestConfigurationTask>("verifyPitestConfiguration") {
            group = "verification"
            description = "Validates and records the effective sealed PIT configuration."
            projectPathInput.set(target.path)
            targetGlob.set(verified.flatMap { it.targetClasses }.map { it.single() })
            pitestVersion.set(extension.pitestVersion)
            threads.set(verified.flatMap { it.threads })
            enforcementPhase.set("observe")
            module.observationThreshold?.let(mutationThreshold::set)
            effectiveValues.set(
                target.providers.provider {
                    val task = verified.get()
                    sortedMapOf(
                        "addJUnitPlatformLauncher" to extension.addJUnitPlatformLauncher.get().toString(),
                        "detectInlinedCode" to task.detectInlinedCode.get().toString(),
                        "defaultCharacterEncoding" to task.defaultCharacterEncoding.orEmpty(),
                        "enableDefaultIncrementalAnalysis" to task.enableDefaultIncrementalAnalysis.get().toString(),
                        "failWhenNoMutations" to task.failWhenNoMutations.get().toString(),
                        "fullMutationMatrix" to task.fullMutationMatrix.get().toString(),
                        "inputCharset" to task.inputEncoding.get().name(),
                        "mutationEngine" to task.mutationEngine.get(),
                        "outputCharset" to task.outputEncoding.get().name(),
                        "outputFormats" to task.outputFormats.get().sorted().joinToString(","),
                        "skipFailingTests" to task.skipFailingTests.get().toString(),
                        "timeoutConstInMillis" to task.timeoutConstInMillis.get().toString(),
                        "timeoutFactor" to task.timeoutFactor.get().toPlainString(),
                        "timestampedReports" to task.timestampedReports.get().toString(),
                        "useClasspathFile" to task.useAdditionalClasspathFile.get().toString(),
                        "useClasspathJar" to task.useClasspathJar.get().toString(),
                        "verbose" to task.verbose.get().toString(),
                        "verbosity" to task.verbosity.get(),
                    )
                },
            )
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
