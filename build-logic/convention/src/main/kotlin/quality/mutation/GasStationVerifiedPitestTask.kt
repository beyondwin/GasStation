package info.solidsoft.gradle.pitest

import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Optional
import java.io.File
import java.security.MessageDigest

/** The only supported PIT execution surface in GasStation. */
abstract class GasStationVerifiedPitestTask : PitestTask() {
    /** Immutable canonical snapshot shared by configuration evidence and the pre-super guard. */
    @get:Input
    abstract val expectedEffectiveSurface: MapProperty<String, String>

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val expectedSourceDirs: org.gradle.api.file.ConfigurableFileCollection
    @get:InputFiles @get:Classpath
    abstract val expectedAdditionalClasspath: org.gradle.api.file.ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val expectedMutableCodePaths: org.gradle.api.file.ConfigurableFileCollection
    @get:InputFiles @get:Classpath
    abstract val expectedLaunchClasspath: org.gradle.api.file.ConfigurableFileCollection

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val observedOriginalSourceDirs: org.gradle.api.file.ConfigurableFileCollection
    @get:InputFiles @get:Classpath
    abstract val observedOriginalAdditionalClasspath: org.gradle.api.file.ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val observedOriginalMutableCodePaths: org.gradle.api.file.ConfigurableFileCollection
    @get:InputFiles @get:Classpath
    abstract val observedOriginalLaunchClasspath: org.gradle.api.file.ConfigurableFileCollection

    @get:Input
    abstract val expectedChildEnvironment: org.gradle.api.provider.MapProperty<String, String>

    @get:org.gradle.api.tasks.Internal
    abstract val expectedRepositoryRoot: org.gradle.api.file.DirectoryProperty

    @get:org.gradle.api.tasks.Internal
    abstract val expectedBuildDirectory: org.gradle.api.file.DirectoryProperty

    @get:Input
    @get:Optional
    abstract val expectedMutationThreshold: org.gradle.api.provider.Property<Int>

    override fun exec() {
        validateExecutionSurface()
        super.exec()
    }

    internal fun validateExecutionSurface() {
        fun reject(condition: Boolean, surface: String) {
            if (condition) throw GradleException("Unsupported pitestVerified execution surface: $surface")
        }

        validatePitestOptionOverrides(overriddenTargetTests, additionalFeatures, overriddenVerbose)
        reject(args.orEmpty().isNotEmpty(), "--args")
        reject(argumentProviders.isNotEmpty(), "argumentProviders")
        reject(jvmArgumentProviders.isNotEmpty(), "jvmArgumentProviders")
        reject(jvmArgs.orEmpty().isNotEmpty(), "jvmArgs")
        reject(jvmArguments.getOrElse(emptyList()).isNotEmpty(), "jvmArguments")
        reject(systemProperties.isNotEmpty(), "systemProperties")
        validateSealedEncodingSurface(
            defaultCharacterEncoding,
            jvmArgs.orEmpty(),
            systemProperties,
            allJvmArgs,
        )
        reject(!bootstrapClasspath.isEmpty, "bootstrapClasspath")
        reject(minHeapSize != null, "minHeapSize")
        reject(maxHeapSize != null, "maxHeapSize")
        reject(enableAssertions, "enableAssertions")
        reject(debug, "debug")
        reject(debugOptions.enabled.get(), "debugOptions.enabled")
        reject(debugOptions.host.get() != "localhost", "debugOptions.host")
        reject(debugOptions.port.get() != 5005, "debugOptions.port")
        reject(!debugOptions.server.get(), "debugOptions.server")
        reject(!debugOptions.suspend.get(), "debugOptions.suspend")
        reject(mainModule.isPresent, "mainModule")
        reject(modularity.inferModulePath.getOrElse(false), "modularity.inferModulePath")
        reject(isIgnoreExitValue, "ignoreExitValue")
        reject(mainClass.orNull != "org.pitest.mutationtest.commandline.MutationCoverageReport", "mainClass")
        val launcher = javaLauncher.get()
        reject(launcher.metadata.languageVersion.asInt() != 21, "javaLauncher.languageVersion")
        reject(
            launcher.metadata.vendor.lowercase().let { "adoptium" !in it && "temurin" !in it },
            "javaLauncher.vendor",
        )
        validateSealedExecutable(executable, launcher.executablePath.asFile)
        reject(workingDir.canonicalFile != expectedRepositoryRoot.get().asFile.canonicalFile, "workingDir")
        reject(environment != expectedChildEnvironment.get(), "environment")
        reject(classpath.files != launchClasspath.files, "classpath")
        reject(testPlugin.isPresent, "testPlugin")
        val expectedBuildDirectoryFile = expectedBuildDirectory.get().asFile
        reject(reportDir.get().asFile.canonicalFile != expectedBuildDirectoryFile.resolve("reports/pitest").canonicalFile, "reportDir")
        reject(
            additionalClasspathFile.get().asFile.canonicalFile != expectedBuildDirectoryFile.resolve("pitClasspath").canonicalFile,
            "additionalClasspathFile",
        )
        reject(
            defaultFileForHistoryData.get().asFile.canonicalFile != expectedBuildDirectoryFile.resolve("pitHistory.txt").canonicalFile,
            "defaultFileForHistoryData",
        )
        reject(mainProcessJvmArgs.getOrElse(emptyList()).isNotEmpty(), "mainProcessJvmArgs")
        reject(childProcessJvmArgs.getOrElse(emptyList()).isNotEmpty(), "childProcessJvmArgs")
        reject(historyInputLocation.isPresent, "historyInputLocation")
        reject(historyOutputLocation.isPresent, "historyOutputLocation")
        reject(jvmPath.isPresent, "jvmPath")
        reject(coverageThreshold.isPresent, "coverageThreshold")
        reject(testStrengthThreshold.isPresent, "testStrengthThreshold")
        reject(maxSurviving.isPresent, "maxSurviving")
        val actualThreshold = mutationThreshold.orNull
        reject(actualThreshold != expectedMutationThreshold.orNull, "mutationThreshold")
        validateEffectivePitestSurface(
            expectedEffectivePitestSurface().filterKeys { !it.startsWith("extension.") },
            effectivePitestSurface(expectedRepositoryRoot.get().asFile),
        )
        val repositoryRoot = expectedRepositoryRoot.get().asFile
        val expectedCollections = mapOf(
            "sourceDirs" to fileCollectionIdentity(expectedSourceDirs, repositoryRoot),
            "additionalClasspath" to fileCollectionIdentity(expectedAdditionalClasspath, repositoryRoot),
            "mutableCodePaths" to fileCollectionIdentity(expectedMutableCodePaths, repositoryRoot),
            "launchClasspath" to fileCollectionIdentity(expectedLaunchClasspath, repositoryRoot),
        )
        val originalCollections = mapOf(
            "sourceDirs" to fileCollectionIdentity(observedOriginalSourceDirs, repositoryRoot),
            "additionalClasspath" to fileCollectionIdentity(observedOriginalAdditionalClasspath, repositoryRoot),
            "mutableCodePaths" to fileCollectionIdentity(observedOriginalMutableCodePaths, repositoryRoot),
            "launchClasspath" to fileCollectionIdentity(observedOriginalLaunchClasspath, repositoryRoot),
        )
        val changedOriginal = expectedCollections.keys.sorted().filter {
            expectedCollections[it] != originalCollections[it]
        }
        reject(
            changedOriginal.isNotEmpty(),
            changedOriginal.joinToString(",") { "original.pit.$it" },
        )
    }

    internal fun expectedEffectivePitestSurface(): Map<String, String> {
        val repositoryRoot = expectedRepositoryRoot.get().asFile
        return expectedEffectiveSurface.get().toMutableMap().also { surface ->
            surface["pit.sourceDirs"] = fileCollectionIdentity(expectedSourceDirs, repositoryRoot)
            surface["pit.additionalClasspath"] = fileCollectionIdentity(expectedAdditionalClasspath, repositoryRoot)
            surface["pit.mutableCodePaths"] = fileCollectionIdentity(expectedMutableCodePaths, repositoryRoot)
            surface["pit.launchClasspath"] = fileCollectionIdentity(expectedLaunchClasspath, repositoryRoot)
            surface["java.classpath"] = fileCollectionIdentity(expectedLaunchClasspath, repositoryRoot)
            surface["java.bootstrapClasspath"] = ""
            surface["derivedCli.sourceDirs"] = surface.getValue("pit.sourceDirs")
            surface["derivedCli.mutableCodePaths"] = surface.getValue("pit.mutableCodePaths")
        }.toSortedMap()
    }

    internal fun effectivePitestSurface(repositoryRoot: File, resolveFiles: Boolean = true): Map<String, String> {
        fun values(raw: Iterable<String>?): String = raw?.sorted()?.joinToString("\u001f") ?: "<null>"
        fun ordered(raw: Iterable<String>?): String = raw?.joinToString("\u001f") ?: "<null>"
        fun value(raw: Any?): String = raw?.toString() ?: "<null>"
        fun path(raw: File?): String = raw?.let { canonicalIdentity(it, repositoryRoot) } ?: "<null>"
        fun location(raw: File?): String = raw?.let {
            val canonical = it.canonicalFile
            val root = repositoryRoot.canonicalFile
            if (canonical.toPath().startsWith(root.toPath())) {
                "repo:${root.toPath().relativize(canonical.toPath()).toString().replace(File.separatorChar, '/')}"
            } else {
                "external:${canonical.name}"
            }
        } ?: "<null>"
        fun files(raw: FileCollection): String = if (resolveFiles) fileCollectionIdentity(raw, repositoryRoot) else "<deferred>"

        // Gradle's aggregate JVM-argument getters realize argument providers. The
        // surface guard must reject provider presence without executing untrusted
        // provider code, so only inspect aggregates when the provider set is empty.
        val hasJvmArgumentProviders = jvmArgumentProviders.isNotEmpty()
        val aggregateJvmArguments = if (hasJvmArgumentProviders) null else jvmArguments.orNull
        val managedEncodingArguments = if (hasJvmArgumentProviders) {
            "<provider-present>"
        } else {
            allJvmArgs.filter(::isFileEncodingArgument).joinToString("\u001f")
        }
        val surface = sortedMapOf(
            "pit.testPlugin" to value(testPlugin.orNull),
            "pit.reportDir" to location(reportDir.orNull?.asFile),
            "pit.targetClasses" to values(targetClasses.orNull),
            "pit.targetTests" to values(targetTests.orNull),
            "pit.threads" to value(threads.orNull),
            "pit.mutators" to values(mutators.orNull),
            "pit.excludedMethods" to values(excludedMethods.orNull),
            "pit.excludedClasses" to values(excludedClasses.orNull),
            "pit.excludedTestClasses" to values(excludedTestClasses.orNull),
            "pit.avoidCallsTo" to values(avoidCallsTo.orNull),
            "pit.verbose" to value(verbose.orNull),
            "pit.verbosity" to value(verbosity.orNull),
            "pit.timeoutFactor" to value(timeoutFactor.orNull?.toPlainString()),
            "pit.timeoutConstInMillis" to value(timeoutConstInMillis.orNull),
            "pit.childProcessJvmArgs" to ordered(childProcessJvmArgs.orNull),
            "pit.outputFormats" to values(outputFormats.orNull),
            "pit.failWhenNoMutations" to value(failWhenNoMutations.orNull),
            "pit.skipFailingTests" to value(skipFailingTests.orNull),
            "pit.includedGroups" to values(includedGroups.orNull),
            "pit.excludedGroups" to values(excludedGroups.orNull),
            "pit.fullMutationMatrix" to value(fullMutationMatrix.orNull),
            "pit.includedTestMethods" to values(includedTestMethods.orNull),
            "pit.sourceDirs" to files(sourceDirs),
            "pit.detectInlinedCode" to value(detectInlinedCode.orNull),
            "pit.timestampedReports" to value(timestampedReports.orNull),
            "pit.additionalClasspath" to files(additionalClasspath),
            "pit.useClasspathFile" to value(useAdditionalClasspathFile.orNull),
            "pit.additionalClasspathFile" to location(additionalClasspathFile.orNull?.asFile),
            "pit.mutableCodePaths" to files(mutableCodePaths),
            "pit.historyInputLocation" to path(historyInputLocation.orNull?.asFile),
            "pit.historyOutputLocation" to path(historyOutputLocation.orNull?.asFile),
            "pit.enableDefaultIncrementalAnalysis" to value(enableDefaultIncrementalAnalysis.orNull),
            "pit.defaultFileForHistoryData" to location(defaultFileForHistoryData.orNull?.asFile),
            "pit.mutationThreshold" to value(mutationThreshold.orNull),
            "pit.coverageThreshold" to value(coverageThreshold.orNull),
            "pit.testStrengthThreshold" to value(testStrengthThreshold.orNull),
            "pit.mutationEngine" to value(mutationEngine.orNull),
            "pit.exportLineCoverage" to value(exportLineCoverage.orNull),
            "pit.jvmPath" to path(jvmPath.orNull?.asFile),
            "pit.mainProcessJvmArgs" to ordered(mainProcessJvmArgs.orNull),
            "pit.launchClasspath" to files(launchClasspath),
            "pit.pluginConfiguration" to pluginConfiguration.orNull.orEmpty().toSortedMap().entries.joinToString("\u001f") { "${it.key}=${it.value}" },
            "pit.maxSurviving" to value(maxSurviving.orNull),
            "pit.useClasspathJar" to value(useClasspathJar.orNull),
            "pit.inputEncoding" to value(inputEncoding.orNull?.name()),
            "pit.outputEncoding" to value(outputEncoding.orNull?.name()),
            "pit.features" to ordered(features.orNull),
            "cli.overriddenTargetTests" to ordered(overriddenTargetTests),
            "cli.additionalFeatures" to ordered(additionalFeatures),
            "cli.overriddenVerbose" to value(overriddenVerbose),
            "java.args" to ordered(args),
            "java.argumentProviderCount" to argumentProviders.size.toString(),
            "java.jvmArgs" to ordered(jvmArgs),
            "java.jvmArguments" to ordered(aggregateJvmArguments),
            "java.jvmArgumentProviderCount" to jvmArgumentProviders.size.toString(),
            "java.systemProperties" to systemProperties.toSortedMap().entries.joinToString("\u001f") { "${it.key}=${it.value}" },
            "java.bootstrapClasspath" to files(bootstrapClasspath),
            "java.minHeapSize" to value(minHeapSize),
            "java.maxHeapSize" to value(maxHeapSize),
            "java.enableAssertions" to enableAssertions.toString(),
            "java.debug" to debug.toString(),
            "java.debug.enabled" to debugOptions.enabled.get().toString(),
            "java.debug.host" to debugOptions.host.get(),
            "java.debug.port" to debugOptions.port.get().toString(),
            "java.debug.server" to debugOptions.server.get().toString(),
            "java.debug.suspend" to debugOptions.suspend.get().toString(),
            "java.defaultCharacterEncoding" to value(defaultCharacterEncoding),
            "java.mainClass" to value(mainClass.orNull),
            "java.mainModule" to value(mainModule.orNull),
            "java.inferModulePath" to modularity.inferModulePath.getOrElse(false).toString(),
            "java.ignoreExitValue" to isIgnoreExitValue.toString(),
            "java.workingDir" to location(workingDir),
            "java.environment" to canonicalEnvironmentIdentity(
                environment.mapValues { it.value.toString() },
                repositoryRoot,
            ),
            "java.classpath" to files(classpath),
            "java.executable" to path(executable?.let(::File)),
            "command.managedEncodingArguments" to managedEncodingArguments,
        )
        val cli = sortedMapOf(
            "targetClasses" to surface.getValue("pit.targetClasses"),
            "targetTests" to surface.getValue("pit.targetTests"),
            "threads" to surface.getValue("pit.threads"),
            "mutators" to surface.getValue("pit.mutators"),
            "excludedMethods" to surface.getValue("pit.excludedMethods"),
            "excludedClasses" to surface.getValue("pit.excludedClasses"),
            "excludedTestClasses" to surface.getValue("pit.excludedTestClasses"),
            "avoidCallsTo" to surface.getValue("pit.avoidCallsTo"),
            "verbose" to surface.getValue("pit.verbose"),
            "verbosity" to surface.getValue("pit.verbosity"),
            "timeoutFactor" to surface.getValue("pit.timeoutFactor"),
            "timeoutConst" to surface.getValue("pit.timeoutConstInMillis"),
            "jvmArgs" to surface.getValue("pit.childProcessJvmArgs"),
            "outputFormats" to surface.getValue("pit.outputFormats"),
            "failWhenNoMutations" to surface.getValue("pit.failWhenNoMutations"),
            "skipFailingTests" to surface.getValue("pit.skipFailingTests"),
            "includedGroups" to surface.getValue("pit.includedGroups"),
            "excludedGroups" to surface.getValue("pit.excludedGroups"),
            "fullMutationMatrix" to surface.getValue("pit.fullMutationMatrix"),
            "includedTestMethods" to surface.getValue("pit.includedTestMethods"),
            "sourceDirs" to surface.getValue("pit.sourceDirs"),
            "detectInlinedCode" to surface.getValue("pit.detectInlinedCode"),
            "timestampedReports" to surface.getValue("pit.timestampedReports"),
            "mutableCodePaths" to surface.getValue("pit.mutableCodePaths"),
            "mutationThreshold" to surface.getValue("pit.mutationThreshold"),
            "coverageThreshold" to surface.getValue("pit.coverageThreshold"),
            "testStrengthThreshold" to surface.getValue("pit.testStrengthThreshold"),
            "mutationEngine" to surface.getValue("pit.mutationEngine"),
            "exportLineCoverage" to surface.getValue("pit.exportLineCoverage"),
            "jvmPath" to surface.getValue("pit.jvmPath"),
            "maxSurviving" to surface.getValue("pit.maxSurviving"),
            "useClasspathJar" to surface.getValue("pit.useClasspathJar"),
            "inputEncoding" to surface.getValue("pit.inputEncoding"),
            "outputEncoding" to surface.getValue("pit.outputEncoding"),
            "features" to surface.getValue("pit.features"),
            "classPathFile" to surface.getValue("pit.additionalClasspathFile"),
        )
        cli.forEach { (name, entry) -> surface["derivedCli.$name"] = entry }
        surface["derivedCli.pluginConfiguration"] = surface.getValue("pit.pluginConfiguration")
        return surface
    }
}

internal fun validateEffectivePitestSurface(expected: Map<String, String>, actual: Map<String, String>) {
    if (actual != expected) {
        val changed = (expected.keys + actual.keys).sorted().filter { expected[it] != actual[it] }
        throw GradleException("Unsupported pitestVerified effective PIT surface: ${changed.joinToString(",")}")
    }
}

private fun normalizeEnvironmentValue(name: String, value: String, repositoryRoot: File): String =
    if (name in setOf("HOME", "TMPDIR")) canonicalIdentity(File(value), repositoryRoot) else value

internal fun canonicalEnvironmentIdentity(environment: Map<String, String>, repositoryRoot: File): String =
    environment.toSortedMap().entries.joinToString("\u001f") {
        "${it.key}=${normalizeEnvironmentValue(it.key, it.value, repositoryRoot)}"
    }

internal fun fileCollectionIdentity(files: FileCollection, repositoryRoot: File): String =
    files.files.map { canonicalIdentity(it, repositoryRoot) }.sorted().joinToString("\u001f")

internal fun canonicalIdentity(file: File, repositoryRoot: File): String {
    val canonicalRoot = repositoryRoot.canonicalFile
    val canonical = file.canonicalFile
    val dependencyCache = canonicalRoot.resolve("build/quality/pitest-runtime/gradle-user-home/caches").toPath()
    val relative = canonical.toPath().takeIf {
        it.startsWith(canonicalRoot.toPath()) && !it.startsWith(dependencyCache)
    }
        ?.let { canonicalRoot.toPath().relativize(it).toString().replace(File.separatorChar, '/') }
    if (relative != null) return "repo:$relative:${if (canonical.isDirectory) "directory" else if (canonical.isFile) "file" else "missing"}"
    val digest = if (canonical.isFile) sha256(canonical.readBytes()) else "missing"
    return "external:${canonical.name}:$digest"
}

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal fun validateSealedEncodingSurface(
    defaultCharacterEncoding: String?,
    explicitJvmArguments: List<String>,
    mutableSystemProperties: Map<String, *>,
    effectiveJvmArguments: List<String>,
) {
    if (defaultCharacterEncoding != "UTF-8") {
        throw GradleException("Unsupported pitestVerified execution surface: file.encoding/defaultCharacterEncoding")
    }
    if (explicitJvmArguments.any(::isFileEncodingArgument)) {
        throw GradleException("Unsupported pitestVerified execution surface: alternate file.encoding JVM argument")
    }
    if (mutableSystemProperties.containsKey("file.encoding")) {
        throw GradleException("Unsupported pitestVerified execution surface: alternate file.encoding system property")
    }
    val managedEncodingArguments = effectiveJvmArguments.filter(::isFileEncodingArgument)
    if (managedEncodingArguments != listOf("-Dfile.encoding=UTF-8")) {
        throw GradleException("Unsupported pitestVerified execution surface: file.encoding must have one managed UTF-8 argument")
    }
}

private fun isFileEncodingArgument(argument: String): Boolean =
    argument == "-Dfile.encoding" || argument.startsWith("-Dfile.encoding=")

internal fun validateSealedExecutable(executable: String?, launcherExecutable: File) {
    if (executable == null || File(executable).canonicalFile != launcherExecutable.canonicalFile) {
        throw GradleException(
            "Unsupported pitestVerified execution surface: executable/javaLauncher " +
                "(executable=${executable?.let { File(it).canonicalFile }}, launcher=${launcherExecutable.canonicalFile})",
        )
    }
}

internal fun validatePitestOptionOverrides(
    overriddenTargetTests: List<String>?,
    additionalFeatures: List<String>?,
    overriddenVerbose: Boolean?,
) {
    val rejected =
        when {
            !overriddenTargetTests.isNullOrEmpty() -> "--targetTests"
            !additionalFeatures.isNullOrEmpty() -> "--additionalFeatures"
            overriddenVerbose != null -> "--verbose"
            else -> null
        }
    if (rejected != null) {
        throw GradleException("Unsupported pitestVerified execution surface: $rejected")
    }
}
