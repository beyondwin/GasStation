package com.gasstation.buildlogic.quality.mutation

import com.gasstation.buildlogic.quality.coverage.canonicalCoverageJson
import com.gasstation.buildlogic.quality.mutation.blockingMutationThreshold
import info.solidsoft.gradle.pitest.fileCollectionIdentity
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest

abstract class VerifyPitestConfigurationTask : DefaultTask() {
    @get:Input abstract val projectPathInput: Property<String>
    @get:Input abstract val targetGlob: Property<String>
    @get:Input abstract val pitestVersion: Property<String>
    @get:Input abstract val threads: Property<Int>
    @get:Input abstract val enforcementPhase: Property<String>
    @get:Input @get:Optional abstract val mutationThreshold: Property<Int>
    @get:Input abstract val effectiveValues: MapProperty<String, String>
    @get:Input abstract val expectedEffectiveValues: MapProperty<String, String>
    @get:Internal abstract val repositoryRoot: DirectoryProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val actualSourceDirs: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val expectedSourceDirs: ConfigurableFileCollection
    @get:InputFiles @get:Classpath abstract val actualAdditionalClasspath: ConfigurableFileCollection
    @get:InputFiles @get:Classpath abstract val expectedAdditionalClasspath: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val actualMutableCodePaths: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val expectedMutableCodePaths: ConfigurableFileCollection
    @get:InputFiles @get:Classpath abstract val actualLaunchClasspath: ConfigurableFileCollection
    @get:InputFiles @get:Classpath abstract val expectedLaunchClasspath: ConfigurableFileCollection
    @get:Input abstract val directPitestGuardMarker: Property<String>
    @get:InputFile abstract val policyFile: RegularFileProperty
    @get:InputFile abstract val routeFile: RegularFileProperty
    @get:InputFile abstract val routeReceiptFile: RegularFileProperty
    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val expectedTarget = "com.gasstation.domain.${projectPathInput.get().substringAfterLast(':')}.*"
        if (targetGlob.get() != expectedTarget) throw GradleException("PIT target package differs from closed module mapping")
        if (pitestVersion.get() != "1.25.7") throw GradleException("PIT engine must be 1.25.7")
        if (threads.get() != 2) throw GradleException("PIT threads must be exactly 2")
        if (mutationThreshold.orNull != blockingMutationThreshold(projectPathInput.get())) {
            throw GradleException("PIT native mutation threshold differs from the blocking contract")
        }
        if (directPitestGuardMarker.get() != "RejectDirectPitestAction:first") {
            throw GradleException("Direct PIT guard marker differs from the closed contract")
        }
        val values = effectiveValues.get()
        val expected = expectedEffectiveValues.get()
        if (values != expected) throw GradleException("PIT effective values differ from the closed contract: $values")
        val root = repositoryRoot.get().asFile
        val collectionIdentities = sortedMapOf(
            "pit.sourceDirs" to fileCollectionIdentity(actualSourceDirs, root),
            "pit.additionalClasspath" to fileCollectionIdentity(actualAdditionalClasspath, root),
            "pit.mutableCodePaths" to fileCollectionIdentity(actualMutableCodePaths, root),
            "pit.launchClasspath" to fileCollectionIdentity(actualLaunchClasspath, root),
        )
        val expectedCollectionIdentities = sortedMapOf(
            "pit.sourceDirs" to fileCollectionIdentity(expectedSourceDirs, root),
            "pit.additionalClasspath" to fileCollectionIdentity(expectedAdditionalClasspath, root),
            "pit.mutableCodePaths" to fileCollectionIdentity(expectedMutableCodePaths, root),
            "pit.launchClasspath" to fileCollectionIdentity(expectedLaunchClasspath, root),
        )
        if (collectionIdentities != expectedCollectionIdentities) {
            throw GradleException("PIT effective classpath/source surface differs from the closed contract")
        }
        val serializedSurface = values.toMutableMap().also { surface ->
            surface.putAll(collectionIdentities)
            surface["java.classpath"] = collectionIdentities.getValue("pit.launchClasspath")
            surface["java.bootstrapClasspath"] = ""
            surface["derivedCli.sourceDirs"] = collectionIdentities.getValue("pit.sourceDirs")
            surface["derivedCli.mutableCodePaths"] = collectionIdentities.getValue("pit.mutableCodePaths")
        }.toSortedMap()
        val policyBytes = policyFile.get().asFile.readBytes()
        val routeBytes = routeFile.get().asFile.readBytes()
        val routeReceiptBytes = routeReceiptFile.get().asFile.readBytes()
        @Suppress("UNCHECKED_CAST")
        val policy = JsonSlurper().parse(policyBytes) as? Map<String, Any?>
            ?: throw GradleException("PIT policy must be an object")
        validateBlockingEnforcement(enforcementPhase.get(), policy["enforcementPhase"] as? String)
        @Suppress("UNCHECKED_CAST")
        val route = JsonSlurper().parse(routeBytes) as? Map<String, Any?>
            ?: throw GradleException("PIT route evidence must be an object")
        @Suppress("UNCHECKED_CAST")
        val routeReceipt = JsonSlurper().parse(routeReceiptBytes) as? Map<String, Any?>
            ?: throw GradleException("PIT route receipt must be an object")
        val policySha256 = sha256(policyBytes)
        if (route["policySha256"] != policySha256) {
            throw GradleException("PIT route policy identity differs")
        }
        @Suppress("UNCHECKED_CAST")
        val predecessors = routeReceipt["predecessors"] as? Map<String, Any?>
            ?: throw GradleException("PIT route receipt predecessors are missing")
        if (predecessors["policy"] != policySha256 || predecessors["route"] != sha256(routeBytes)) {
            throw GradleException("PIT route receipt predecessor identity differs")
        }
        @Suppress("UNCHECKED_CAST")
        val hostNeutral = route["hostNeutralMutationIdentity"] as? Map<String, Any?>
            ?: throw GradleException("PIT route host-neutral identity is missing")
        @Suppress("UNCHECKED_CAST")
        val policyPitest = policy["pitest"] as? Map<String, Any?>
            ?: throw GradleException("PIT policy report-generation identity is missing")
        @Suppress("UNCHECKED_CAST")
        val policyModules = policy["modules"] as? Map<String, Map<String, Any?>>
            ?: throw GradleException("PIT policy module identity is missing")
        val expectedNeutral = sortedMapOf<String, Any?>(
            "java" to sortedMapOf(
                "major" to 21,
                "toolchainRole" to "mutation-runtime",
                "vendorFamily" to "Eclipse Adoptium/Temurin",
            ),
            "pitestEngine" to policyPitest["pitestVersion"],
            "pitestPlugin" to policyPitest["pluginVersion"],
            "reportGeneration" to policyPitest,
            "schema" to "host-neutral-mutation-identity-v1",
            "targets" to policyModules.toSortedMap().mapValues { (_, module) ->
                sortedMapOf(
                    "sourceSets" to listOf("main", "test"),
                    "targetClasses" to module["targetClasses"],
                    "targetTests" to module["targetTests"],
                )
            },
        )
        if (hostNeutral != expectedNeutral) {
            throw GradleException("PIT route host-neutral identity differs from checked policy")
        }
        @Suppress("UNCHECKED_CAST")
        val routePerRun = route["perRunExecutionProvenance"] as? Map<String, Any?>
            ?: throw GradleException("PIT route per-run provenance is missing")
        val perRun = sortedMapOf<String, Any?>(
            "imageIdentity" to routePerRun["imageIdentity"],
            "javaExecutableSha256" to routePerRun["javaExecutableSha256"],
            "javaRuntimeVersion" to routePerRun["javaRuntimeVersion"],
            "observedToolBundleSha256" to routePerRun["observedToolBundleSha256"],
            "profileDefinitionSha256" to routePerRun["profileDefinitionSha256"],
            "routeReceiptSha256" to sha256(routeReceiptBytes),
            "schema" to "per-run-execution-provenance-configuration-v1",
            "selectedProfile" to routePerRun["selectedProfile"],
        )
        val payload = sortedMapOf<String, Any?>(
            "addJUnitPlatformLauncher" to false,
            "directPitestGuard" to directPitestGuardMarker.get(),
            "defaultCharacterEncoding" to values.getValue("java.defaultCharacterEncoding"),
            "enforcementPhase" to enforcementPhase.get(),
            "environmentPolicy" to "pitest-sealed-v1",
            "hostNeutralMutationIdentity" to hostNeutral,
            "hostNeutralMutationIdentitySha256" to sha256(canonicalCoverageJson(hostNeutral)),
            "mutationThreshold" to mutationThreshold.orNull,
            "perRunExecutionProvenance" to perRun,
            "perRunExecutionProvenanceSha256" to sha256(canonicalCoverageJson(perRun)),
            "pitestVersion" to pitestVersion.get(),
            "pluginVersion" to "1.19.0",
            "policySha256" to policySha256,
            "projectPath" to projectPathInput.get(),
            "routeReceiptSha256" to sha256(routeReceiptBytes),
            "schemaVersion" to 1,
            "targetClasses" to listOf(targetGlob.get()),
            "targetTests" to listOf(targetGlob.get()),
            "threads" to threads.get(),
            "values" to serializedSurface,
        )
        val destination = outputFile.get().asFile
        destination.parentFile.mkdirs()
        destination.writeBytes(canonicalCoverageJson(payload) + byteArrayOf('\n'.code.toByte()))
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
}

internal fun validateBlockingEnforcement(taskPhase: String, policyPhase: String?) {
    if (taskPhase != "blocking" || policyPhase != "blocking") {
        throw GradleException("PIT task and policy must both use the blocking enforcement phase")
    }
}
