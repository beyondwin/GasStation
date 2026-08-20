package info.solidsoft.gradle.pitest

import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import java.io.File

/** The only supported PIT execution surface in GasStation. */
abstract class GasStationVerifiedPitestTask : PitestTask() {
    @get:Input
    abstract val expectedChildEnvironment: org.gradle.api.provider.MapProperty<String, String>

    @get:org.gradle.api.tasks.Internal
    abstract val expectedRepositoryRoot: org.gradle.api.file.DirectoryProperty

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
        reject(jvmArgs.orEmpty().isNotEmpty(), "jvmArgs")
        reject(jvmArguments.getOrElse(emptyList()).isNotEmpty(), "jvmArguments")
        reject(jvmArgumentProviders.isNotEmpty(), "jvmArgumentProviders")
        reject(systemProperties.isNotEmpty(), "systemProperties")
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
        reject(defaultCharacterEncoding != null, "defaultCharacterEncoding")
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
        reject(File(executable).canonicalFile != launcher.executablePath.asFile.canonicalFile, "executable/javaLauncher")
        reject(workingDir.canonicalFile != expectedRepositoryRoot.get().asFile.canonicalFile, "workingDir")
        reject(environment != expectedChildEnvironment.get(), "environment")
        reject(classpath.files != launchClasspath.files, "classpath")
        reject(testPlugin.isPresent, "testPlugin")
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
